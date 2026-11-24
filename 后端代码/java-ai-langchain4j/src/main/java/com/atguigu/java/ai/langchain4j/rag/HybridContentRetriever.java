package com.atguigu.java.ai.langchain4j.rag;

import com.atguigu.java.ai.langchain4j.config.RagProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component("contentRetrieverXiaozhiMilvus")
public class HybridContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridContentRetriever.class);

    private static final int MAX_PARENT_CHARS = 4000;

    private final EmbeddingModel embeddingModel;
    private final MilvusHybridStore hybridStore;
    private final HybridRrfFuser rrfFuser;
    private final RerankService rerankService;
    private final BoostService boostService;
    private final ParentChunkResolver parentChunkResolver;
    private final RagProperties ragProperties;

    public HybridContentRetriever(EmbeddingModel embeddingModel,
                                  MilvusHybridStore hybridStore,
                                  HybridRrfFuser rrfFuser,
                                  RerankService rerankService,
                                  BoostService boostService,
                                  ParentChunkResolver parentChunkResolver,
                                  RagProperties ragProperties) {
        this.embeddingModel = embeddingModel;
        this.hybridStore = hybridStore;
        this.rrfFuser = rrfFuser;
        this.rerankService = rerankService;
        this.boostService = boostService;
        this.parentChunkResolver = parentChunkResolver;
        this.ragProperties = ragProperties;
    }

    @Override
    public List<Content> retrieve(Query query) {
        String queryText = query.text();
        if (!StringUtils.hasText(queryText)) {
            return List.of();
        }

        long start = System.nanoTime();
        List<RetrievalHit> denseHits = searchDense(queryText);
        List<RetrievalHit> sparseHits = searchSparse(queryText);

        if (denseHits.isEmpty() && sparseHits.isEmpty()) {
            log.info("RAG 未检索到内容，queryLength={}", queryText.length());
            log.debug("RAG 未命中，query={}", queryText);
            return List.of();
        }

        List<RetrievalHit> fused = rrfFuser.fuse(
                denseHits, sparseHits, ragProperties.getRrfK(), ragProperties.getRerankCandidateK());
        List<RetrievalHit> reranked = rerankService.rerank(queryText, fused);
        List<RetrievalHit> boosted = boostService.apply(queryText, reranked);
        List<RetrievalHit> uniqueHits = dedupeByParent(boosted).stream()
                .limit(ragProperties.getTopN())
                .toList();

        long costMillis = (System.nanoTime() - start) / 1_000_000;
        log.info("RAG 检索完成，queryLength={}, dense={}, bm25={}, fused={}, candidates={}, topN={}, cost={}ms",
                queryText.length(), denseHits.size(), sparseHits.size(), fused.size(),
                boosted.size(), uniqueHits.size(), costMillis);
        log.debug("RAG 检索 query={}", queryText);

        Map<String, String> parentTexts = resolveParentTexts(uniqueHits);
        List<Content> contents = new ArrayList<>(uniqueHits.size());
        for (int i = 0; i < uniqueHits.size(); i++) {
            RetrievalHit hit = uniqueHits.get(i);
            int citationNo = i + 1;
            log.debug("RAG 命中 citation={} id={} denseRank={} bm25Rank={} rrf={} rerank={} final={} source={} chunk={}",
                    citationNo, hit.getId(), hit.getDenseRank(), hit.getSparseRank(),
                    hit.getRrfScore(), hit.getRerankScore(), hit.getFinalScore(),
                    hit.getSource(), hit.getChunkIndex());

            String location = StringUtils.hasText(hit.getOutlinePath())
                    ? " " + hit.getOutlinePath()
                    : "";
            String contentText = contentText(hit, parentTexts);
            String text = "[来源" + citationNo + "] " + valueOrEmpty(hit.getSource()) + location
                    + " 第" + (hit.getChunkIndex() + 1) + "段\n" + contentText;
            Metadata metadata = new Metadata()
                    .put("citation", "[来源" + citationNo + "]")
                    .put("chunk_id", valueOrEmpty(hit.getId()))
                    .put("source", valueOrEmpty(hit.getSource()))
                    .put("title", valueOrEmpty(hit.getTitle()))
                    .put("department", valueOrEmpty(hit.getDepartment()))
                    .put("doc_key", valueOrEmpty(hit.getDocKey()))
                    .put("content_hash", valueOrEmpty(hit.getContentHash()))
                    .put("chunk_index", hit.getChunkIndex())
                    .put("outline_path", valueOrEmpty(hit.getOutlinePath()))
                    .put("parent_id", valueOrEmpty(hit.getParentId()))
                    .put("role", valueOrEmpty(hit.getRole()))
                    .put("strategy", valueOrEmpty(hit.getStrategy()))
                    .put("rrf_score", hit.getRrfScore())
                    .put("rerank_score", hit.getRerankScore())
                    .put("final_score", hit.getFinalScore());

            contents.add(Content.from(TextSegment.from(text, metadata)));
        }
        return contents;
    }

    private List<RetrievalHit> dedupeByParent(List<RetrievalHit> hits) {
        Map<String, RetrievalHit> unique = new LinkedHashMap<>();
        for (RetrievalHit hit : hits) {
            String key = StringUtils.hasText(hit.getParentId())
                    ? "parent:" + hit.getParentId()
                    : "chunk:" + hit.getId();
            unique.putIfAbsent(key, hit);
        }
        return List.copyOf(unique.values());
    }

    private Map<String, String> resolveParentTexts(List<RetrievalHit> hits) {
        try {
            return parentChunkResolver.resolve(hits);
        } catch (Exception e) {
            log.warn("父块回填失败，使用子块内容: {}", e.getMessage());
            return Map.of();
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String contentText(RetrievalHit hit, Map<String, String> parentTexts) {
        if (!StringUtils.hasText(hit.getParentId())) {
            return hit.getText();
        }
        String parentText = parentTexts.get(hit.getParentId());
        if (!StringUtils.hasText(parentText)) {
            return hit.getText();
        }
        return parentText.length() <= MAX_PARENT_CHARS ? parentText : hit.getText();
    }

    private List<RetrievalHit> searchDense(String queryText) {
        try {
            Embedding embedding = embeddingModel.embed(queryText).content();
            if (embedding == null) {
                throw new IllegalStateException("Embedding 返回为空");
            }
            int dimension = embedding.dimension();
            if (dimension != ragProperties.getEmbeddingDimension()) {
                throw new IllegalStateException("Embedding 维度不一致: expected="
                        + ragProperties.getEmbeddingDimension() + ", actual=" + dimension);
            }
            return hybridStore.searchDense(embedding.vector(), ragProperties.getCandidateK());
        } catch (Exception e) {
            log.warn("Dense 检索失败，降级为 BM25: {}", e.getMessage());
            return List.of();
        }
    }

    private List<RetrievalHit> searchSparse(String queryText) {
        try {
            return hybridStore.searchSparse(queryText, ragProperties.getCandidateK());
        } catch (Exception e) {
            log.warn("BM25 检索失败，仅使用 Dense 结果: {}", e.getMessage());
            return List.of();
        }
    }
}
