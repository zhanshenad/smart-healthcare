package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.common.HashUtils;
import com.atguigu.java.ai.langchain4j.config.RagProperties;
import com.atguigu.java.ai.langchain4j.entity.KnowledgeChunk;
import com.atguigu.java.ai.langchain4j.entity.KnowledgeDocument;
import com.atguigu.java.ai.langchain4j.mapper.KnowledgeChunkMapper;
import com.atguigu.java.ai.langchain4j.mapper.KnowledgeDocumentMapper;
import com.atguigu.java.ai.langchain4j.rag.HybridChunk;
import com.atguigu.java.ai.langchain4j.rag.MilvusHybridStore;
import com.atguigu.java.ai.langchain4j.service.chunking.StructureAwareChunker;
import com.atguigu.java.ai.langchain4j.service.parser.MarkdownConversionService;
import com.atguigu.java.ai.langchain4j.service.parser.ParsedDocument;
import com.atguigu.java.ai.langchain4j.vo.KnowledgeIngestResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";

    // 主键截断到 32 位，留出足够余量
    private static final int MILVUS_ID_MAX_LENGTH = 32;

    private final DocumentParseService parseService;
    private final MarkdownConversionService conversionService;
    private final StructureAwareChunker structureAwareChunker;
    private final EmbeddingModel embeddingModel;
    private final MilvusHybridStore hybridStore;
    private final KnowledgeChunkWriter chunkWriter;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final RagProperties ragProperties;

    public DocumentIngestionService(DocumentParseService parseService,
                                    MarkdownConversionService conversionService,
                                    StructureAwareChunker structureAwareChunker,
                                    EmbeddingModel embeddingModel,
                                    MilvusHybridStore hybridStore,
                                    KnowledgeChunkWriter chunkWriter,
                                    KnowledgeDocumentMapper documentMapper,
                                    KnowledgeChunkMapper chunkMapper,
                                    RagProperties ragProperties) {
        this.parseService = parseService;
        this.conversionService = conversionService;
        this.structureAwareChunker = structureAwareChunker;
        this.embeddingModel = embeddingModel;
        this.hybridStore = hybridStore;
        this.chunkWriter = chunkWriter;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.ragProperties = ragProperties;
    }

    public KnowledgeIngestResult ingest(Path file, String originalName, String department) {
        return ingest(file, originalName, department, false);
    }

    public KnowledgeIngestResult ingest(Path file, String originalName,
                                        String department, boolean force) {
        String source = normalizeSource(originalName);
        String docKey = HashUtils.sha256(source + "|" + (department == null ? "" : department));
        String docHash = hashFile(file);

        KnowledgeDocument document = findByDocKey(docKey);
        if (!force && document != null
                && docHash.equals(document.getDocHash())
                && STATUS_READY.equals(document.getStatus())) {
            log.info("文档未变化，跳过入库，docKey={}, source={}", docKey, source);
            return toResult(document, true);
        }

        document = savePendingDocument(document, docKey, source, department, docHash,
                source, docTypeOf(file));

        List<String> chunkIds = List.of();
        boolean dbReplaced = false;
        try {
            ParsedDocument parsed = parseService.parse(file);
            parsed = ensureTitle(parsed, source);
            parsed = conversionService.convert(file, parsed);
            if (parsed.markdown() == null || parsed.markdown().isBlank()) {
                throw ApiException.badRequest("文档解析结果为空，可能是扫描件或加密文件");
            }
            updateParsedDocument(document, parsed);

            List<TextSegment> chunks = split(parsed, docKey, source, department, docHash);
            if (chunks.isEmpty()) {
                throw ApiException.badRequest("文档切片为空，请检查文档内容");
            }

            chunkIds = chunks.stream()
                    .map(chunk -> chunk.metadata().getString("chunk_id"))
                    .toList();

            List<TextSegment> indexedChunks = chunks.stream()
                    .filter(chunk -> !"parent".equals(chunk.metadata().getString("role")))
                    .toList();
            List<Embedding> embeddings = embed(indexedChunks);
            List<HybridChunk> hybridChunks = toHybridChunks(indexedChunks, embeddings);
            List<String> staleChunkIds = staleChunkIds(document, chunkIds);

            hybridStore.upsert(hybridChunks);
            chunkWriter.replace(document.getId(), docKey, chunks);
            dbReplaced = true;
            hybridStore.deleteByIds(staleChunkIds);
            markReady(document, chunks.size());
            log.info("文档入库完成，docKey={}, chunks={}", docKey, chunks.size());
            return toResult(document, false);
        } catch (ApiException e) {
            markFailed(document, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("文档入库失败，docKey={}", docKey, e);
            if (!dbReplaced) {
                cleanupNewData(chunkIds);
            }
            markFailed(document, e.getMessage());
            throw new ApiException(500, "文档入库失败: " + e.getMessage());
        }
    }

    public List<KnowledgeDocument> listDocuments() {
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .orderByDesc(KnowledgeDocument::getUpdatedAt));
    }

    private KnowledgeDocument savePendingDocument(KnowledgeDocument document,
                                                   String docKey,
                                                   String source,
                                                   String department,
                                                   String docHash,
                                                   String title,
                                                   String docType) {
        if (document == null) {
            document = new KnowledgeDocument();
            document.setDocKey(docKey);
            document.setSource(source);
            document.setTitle(title);
            document.setDocType(docType);
            document.setDepartment(department);
            document.setDocHash(docHash);
            document.setStatus(STATUS_PENDING);
            document.setChunkCount(0);
            document.setErrorMessage("");
            documentMapper.insert(document);
            return document;
        }

        document.setTitle(title);
        document.setDocType(docType);
        document.setDepartment(department);
        document.setDocHash(docHash);
        document.setStatus(STATUS_PENDING);
        document.setErrorMessage("");
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, document.getId())
                .set(KnowledgeDocument::getTitle, title)
                .set(KnowledgeDocument::getDocType, docType)
                .set(KnowledgeDocument::getDepartment, department)
                .set(KnowledgeDocument::getDocHash, docHash)
                .set(KnowledgeDocument::getStatus, STATUS_PENDING)
                .set(KnowledgeDocument::getErrorMessage, ""));
        return document;
    }

    private void updateParsedDocument(KnowledgeDocument document, ParsedDocument parsed) {
        document.setTitle(parsed.title());
        document.setDocType(parsed.docType());
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, document.getId())
                .set(KnowledgeDocument::getTitle, parsed.title())
                .set(KnowledgeDocument::getDocType, parsed.docType()));
    }

    private List<TextSegment> split(ParsedDocument parsed,
                                    String docKey,
                                    String source,
                                    String department,
                                    String docHash) {
        Metadata metadata = new Metadata()
                .put("doc_key", docKey)
                .put("doc_hash", docHash)
                .put("source", source)
                .put("title", parsed.title())
                .put("doc_type", parsed.docType())
                .put("department", department == null ? "" : department)
                .put("structure_quality", parsed.structureQuality())
                .put("parser", parsed.parserName());

        List<TextSegment> segments = structureAwareChunker.chunk(parsed.markdown(), metadata);
        List<TextSegment> chunks = new ArrayList<>();
        Map<String, String> parentIds = new HashMap<>();

        int chunkIndex = 0;
        for (TextSegment segment : segments) {
            String content = segment.text().strip();
            if (content.isBlank()) {
                continue;
            }

            String contentHash = HashUtils.sha256(content);
            String chunkId = HashUtils.sha256(docKey + ":" + docHash + ":" + chunkIndex + ":" + contentHash)
                    .substring(0, MILVUS_ID_MAX_LENGTH);
            Metadata chunkMetadata = segment.metadata().copy()
                    .put("chunk_id", chunkId)
                    .put("chunk_index", chunkIndex)
                    .put("content_hash", contentHash);

            TextSegment chunk = TextSegment.from(content, chunkMetadata);
            if ("parent".equals(chunkMetadata.getString("role"))) {
                parentIds.put(chunkMetadata.getString("parent_key"), chunkId);
            }
            chunks.add(chunk);
            chunkIndex++;
        }

        for (TextSegment chunk : chunks) {
            if ("parent".equals(chunk.metadata().getString("role"))) {
                continue;
            }
            String parentKey = chunk.metadata().getString("parent_key");
            if (parentKey != null && !parentKey.isBlank()) {
                String parentId = parentIds.get(parentKey);
                if (parentId != null) {
                    chunk.metadata().put("parent_id", parentId);
                }
            }
        }

        return chunks;
    }

    private List<Embedding> embed(List<TextSegment> chunks) {
        Response<List<Embedding>> response = embeddingModel.embedAll(chunks);
        List<Embedding> embeddings = response.content();
        if (embeddings == null || embeddings.size() != chunks.size()) {
            throw new IllegalStateException("Embedding 数量与切片数量不一致");
        }

        int expectedDimension = ragProperties.getEmbeddingDimension();
        for (Embedding embedding : embeddings) {
            if (embedding.dimension() != expectedDimension) {
                throw new IllegalStateException("Embedding 维度不一致: expected="
                        + expectedDimension + ", actual=" + embedding.dimension());
            }
        }
        return embeddings;
    }

    private List<HybridChunk> toHybridChunks(List<TextSegment> chunks, List<Embedding> embeddings) {
        List<HybridChunk> hybridChunks = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TextSegment chunk = chunks.get(i);
            Metadata metadata = chunk.metadata();
            hybridChunks.add(HybridChunk.builder()
                    .id(metadata.getString("chunk_id"))
                    .text(chunk.text())
                    .dense(embeddings.get(i).vector())
                    .docKey(metadata.getString("doc_key"))
                    .docHash(metadata.getString("doc_hash"))
                    .source(metadata.getString("source"))
                    .title(metadata.getString("title"))
                    .docType(metadata.getString("doc_type"))
                    .department(metadata.getString("department"))
                    .chunkIndex(metadata.getInteger("chunk_index"))
                    .contentHash(metadata.getString("content_hash"))
                    .structureQuality(metadata.getString("structure_quality"))
                    .parser(metadata.getString("parser"))
                    .outlinePath(metadata.getString("outline_path"))
                    .parentId(metadata.getString("parent_id"))
                    .role(metadata.getString("role"))
                    .strategy(metadata.getString("strategy"))
                    .build());
        }
        return hybridChunks;
    }

    private List<String> staleChunkIds(KnowledgeDocument document, List<String> newChunkIds) {
        Set<String> newIds = new HashSet<>(newChunkIds);
        return chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                        .eq(KnowledgeChunk::getDocumentId, document.getId()))
                .stream()
                .map(KnowledgeChunk::getChunkId)
                .filter(chunkId -> !newIds.contains(chunkId))
                .toList();
    }

    private void cleanupNewData(List<String> chunkIds) {
        if (chunkIds.isEmpty()) {
            return;
        }
        try {
            chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                    .in(KnowledgeChunk::getChunkId, chunkIds));
            hybridStore.deleteByIds(chunkIds);
        } catch (Exception cleanupError) {
            log.warn("清理失败数据时出错，chunkIds={}", chunkIds, cleanupError);
        }
    }

    private void markReady(KnowledgeDocument document, int chunkCount) {
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, document.getId())
                .set(KnowledgeDocument::getStatus, STATUS_READY)
                .set(KnowledgeDocument::getChunkCount, chunkCount)
                .set(KnowledgeDocument::getErrorMessage, null));
        document.setStatus(STATUS_READY);
        document.setChunkCount(chunkCount);
        document.setErrorMessage(null);
    }

    private void markFailed(KnowledgeDocument document, String message) {
        String errorMessage = message == null ? "未知错误" : message;
        if (errorMessage.length() > 1000) {
            errorMessage = errorMessage.substring(0, 1000);
        }
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, document.getId())
                .set(KnowledgeDocument::getStatus, STATUS_FAILED)
                .set(KnowledgeDocument::getErrorMessage, errorMessage));
        document.setStatus(STATUS_FAILED);
        document.setErrorMessage(errorMessage);
    }

    private KnowledgeDocument findByDocKey(String docKey) {
        return documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getDocKey, docKey));
    }

    private KnowledgeIngestResult toResult(KnowledgeDocument document, boolean skipped) {
        int chunkCount = document.getChunkCount() == null ? 0 : document.getChunkCount();
        return new KnowledgeIngestResult(document.getId(), document.getDocKey(),
                document.getTitle(), chunkCount, skipped, document.getStatus());
    }

    private String hashFile(Path file) {
        try {
            return HashUtils.sha256(Files.readAllBytes(file));
        } catch (IOException e) {
            throw ApiException.badRequest("读取文件失败: " + e.getMessage());
        }
    }

    private ParsedDocument ensureTitle(ParsedDocument parsed, String source) {
        String title = parsed.title();
        if (title == null || title.isBlank() || title.startsWith("knowledge-")) {
            String stem = source;
            int dot = stem.lastIndexOf('.');
            if (dot > 0) {
                stem = stem.substring(0, dot);
            }
            title = stem;
        }
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }
        if (title.equals(parsed.title())) {
            return parsed;
        }
        return new ParsedDocument(parsed.markdown(), title,
                parsed.docType(), parsed.structureQuality(), parsed.parserName());
    }

    private String normalizeSource(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "unknown";
        }
        String name = originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.trim();
    }

    private String docTypeOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "UNKNOWN";
        }
        return name.substring(dot + 1).toUpperCase(Locale.ROOT);
    }
}
