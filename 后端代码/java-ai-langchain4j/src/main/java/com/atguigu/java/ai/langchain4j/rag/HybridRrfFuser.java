package com.atguigu.java.ai.langchain4j.rag;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HybridRrfFuser {

    public List<RetrievalHit> fuse(List<RetrievalHit> denseHits,
                                   List<RetrievalHit> sparseHits,
                                   int rrfK,
                                   int limit) {
        Map<String, RetrievalHit> merged = new LinkedHashMap<>();

        for (int i = 0; i < denseHits.size(); i++) {
            RetrievalHit hit = denseHits.get(i);
            hit.setDenseRank(i + 1);
            hit.setRrfScore(1D / (rrfK + i + 1));
            merged.put(hit.getId(), hit);
        }

        for (int i = 0; i < sparseHits.size(); i++) {
            RetrievalHit sparseHit = sparseHits.get(i);
            RetrievalHit hit = merged.get(sparseHit.getId());
            if (hit == null) {
                hit = sparseHit;
                merged.put(hit.getId(), hit);
                hit.setRrfScore(1D / (rrfK + i + 1));
            } else {
                hit.setRrfScore(hit.getRrfScore() + 1D / (rrfK + i + 1));
            }
            hit.setSparseRank(i + 1);
            hit.setSparseScore(sparseHit.getSparseScore());
        }

        return merged.values().stream()
                .sorted(Comparator.comparingDouble(RetrievalHit::getRrfScore).reversed())
                .limit(limit)
                .toList();
    }
}
