package com.atguigu.java.ai.langchain4j.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.atguigu.java.ai.langchain4j.config.RagProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoostServiceTest {

    private final BoostService boostService = new BoostService(new RagProperties());

    @Test
    void shouldBoostDepartmentTitleAndOfficialDocument() {
        RetrievalHit exact = RetrievalHit.builder()
                .id("1")
                .department("神经内科")
                .title("就诊指南")
                .source("官方就诊指南.md")
                .reranked(true)
                .rerankScore(0.6)
                .build();
        RetrievalHit other = RetrievalHit.builder()
                .id("2")
                .department("心内科")
                .title("就诊指南")
                .source("普通说明.md")
                .reranked(true)
                .rerankScore(0.6)
                .build();

        List<RetrievalHit> boosted = boostService.apply("神经内科就诊指南", List.of(other, exact));

        assertThat(boosted).extracting(RetrievalHit::getId).containsExactly("1", "2");
        assertThat(boosted.get(0).getFinalScore()).isCloseTo(0.6 + 0.05 + 0.03 + 0.02, within(1E-9));
        assertThat(boosted.get(1).getFinalScore()).isCloseTo(0.6 + 0.03, within(1E-9));
    }

    @Test
    void shouldNormalizeRrfScoreWhenRerankIsNotApplied() {
        RetrievalHit first = RetrievalHit.builder()
                .id("1")
                .rrfScore(0.03)
                .reranked(false)
                .build();
        RetrievalHit second = RetrievalHit.builder()
                .id("2")
                .rrfScore(0.015)
                .reranked(false)
                .build();

        List<RetrievalHit> boosted = boostService.apply("普通问题", List.of(first, second));

        assertThat(boosted).extracting(RetrievalHit::getId).containsExactly("1", "2");
        assertThat(boosted.get(0).getFinalScore()).isEqualTo(1D);
        assertThat(boosted.get(1).getFinalScore()).isCloseTo(0.5D, within(1E-9));
    }
}
