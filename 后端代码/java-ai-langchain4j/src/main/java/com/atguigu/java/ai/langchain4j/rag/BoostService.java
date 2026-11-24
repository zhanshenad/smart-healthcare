package com.atguigu.java.ai.langchain4j.rag;

import com.atguigu.java.ai.langchain4j.config.RagProperties;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BoostService {

    private final RagProperties ragProperties;

    public BoostService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<RetrievalHit> apply(String query, List<RetrievalHit> hits) {
        RagProperties.Boost boost = ragProperties.getBoost();
        double maxRrfScore = hits.stream()
                .mapToDouble(RetrievalHit::getRrfScore)
                .max()
                .orElse(0D);

        for (RetrievalHit hit : hits) {
            // RRF 分数远小于 Rerank 分数，先按本批最大值归一化再叠加 Boost
            double score = hit.isReranked()
                    ? hit.getRerankScore()
                    : normalizeRrf(hit.getRrfScore(), maxRrfScore);
            double extra = 0D;

            if (StringUtils.hasText(hit.getDepartment())
                    && query.contains(hit.getDepartment())) {
                extra += boost.getDepartmentWeight();
            }
            if (StringUtils.hasText(hit.getTitle())
                    && query.contains(hit.getTitle())) {
                extra += boost.getTitleWeight();
            }
            if (isOfficialDoc(hit.getSource())) {
                extra += boost.getOfficialWeight();
            }

            hit.setFinalScore(score + extra);
        }

        return hits.stream()
                .sorted(Comparator.comparingDouble(RetrievalHit::getFinalScore).reversed())
                .toList();
    }

    private double normalizeRrf(double rrfScore, double maxRrfScore) {
        return maxRrfScore <= 0D ? 0D : rrfScore / maxRrfScore;
    }

    private boolean isOfficialDoc(String source) {
        if (source == null) {
            return false;
        }
        return source.contains("官方") || source.contains("指南") || source.contains("规范");
    }
}
