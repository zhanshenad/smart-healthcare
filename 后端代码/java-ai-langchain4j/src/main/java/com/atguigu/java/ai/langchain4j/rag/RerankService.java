package com.atguigu.java.ai.langchain4j.rag;

import com.atguigu.java.ai.langchain4j.config.RagProperties;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    private final RerankClient rerankClient;
    private final RagProperties ragProperties;

    public RerankService(RerankClient rerankClient, RagProperties ragProperties) {
        this.rerankClient = rerankClient;
        this.ragProperties = ragProperties;
    }

    public List<RetrievalHit> rerank(String query, List<RetrievalHit> hits) {
        if (hits.size() <= 1 || !isAvailable()) {
            return hits;
        }

        try {
            List<Double> scores = rerankClient.score(
                    query, hits.stream().map(RetrievalHit::getText).toList());
            if (scores.size() != hits.size()) {
                throw new IllegalStateException("Rerank 分数数量与候选数量不一致");
            }

            List<RetrievalHit> reranked = IntStream.range(0, hits.size())
                    .mapToObj(index -> {
                        RetrievalHit hit = hits.get(index);
                        hit.setRerankScore(scores.get(index));
                        hit.setReranked(true);
                        return hit;
                    })
                    .sorted(Comparator.comparingDouble(RetrievalHit::getRerankScore).reversed())
                    .toList();

            log.info("Rerank 完成，候选={}, 最高分={}",
                    hits.size(), reranked.get(0).getRerankScore());
            return reranked;
        } catch (Exception e) {
            log.warn("Rerank 失败，回退到 RRF 排序: {}", e.getMessage());
            return hits;
        }
    }

    private boolean isAvailable() {
        RagProperties.Rerank rerank = ragProperties.getRerank();
        if (!rerank.isEnabled()) {
            return false;
        }
        if (!StringUtils.hasText(rerank.getApiKey())) {
            log.debug("未配置 Rerank API Key，跳过 Rerank");
            return false;
        }
        return true;
    }
}
