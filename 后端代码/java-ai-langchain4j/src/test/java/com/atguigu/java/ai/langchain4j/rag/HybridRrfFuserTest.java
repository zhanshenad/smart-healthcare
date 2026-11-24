package com.atguigu.java.ai.langchain4j.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HybridRrfFuserTest {

    private final HybridRrfFuser fuser = new HybridRrfFuser();

    @Test
    void shouldFuseDenseAndSparseRanks() {
        RetrievalHit a = RetrievalHit.builder().id("a").build();
        RetrievalHit b = RetrievalHit.builder().id("b").build();
        RetrievalHit c = RetrievalHit.builder().id("c").build();

        List<RetrievalHit> fused = fuser.fuse(
                List.of(a, b), List.of(b, c), 60, 10);

        assertThat(fused).extracting(RetrievalHit::getId).containsExactly("b", "a", "c");
        assertThat(fused.get(0).getDenseRank()).isEqualTo(2);
        assertThat(fused.get(0).getSparseRank()).isEqualTo(1);
        assertThat(fused.get(0).getRrfScore()).isEqualTo(1D / 62 + 1D / 61);
        assertThat(fused.get(1).getRrfScore()).isEqualTo(1D / 61);
        assertThat(fused.get(2).getRrfScore()).isEqualTo(1D / 62);
    }

    @Test
    void shouldNotAccumulateWhenFusingTwice() {
        RetrievalHit a = RetrievalHit.builder().id("a").build();
        RetrievalHit b = RetrievalHit.builder().id("b").build();
        RetrievalHit c = RetrievalHit.builder().id("c").build();

        fuser.fuse(List.of(a, b), List.of(b, c), 60, 10);
        List<RetrievalHit> second = fuser.fuse(List.of(a, b), List.of(b, c), 60, 10);

        assertThat(second.get(0).getRrfScore()).isEqualTo(1D / 62 + 1D / 61);
        assertThat(second.get(1).getRrfScore()).isEqualTo(1D / 61);
        assertThat(second.get(2).getRrfScore()).isEqualTo(1D / 62);
    }

    @Test
    void shouldRespectLimit() {
        RetrievalHit a = RetrievalHit.builder().id("a").build();
        RetrievalHit b = RetrievalHit.builder().id("b").build();

        List<RetrievalHit> fused = fuser.fuse(List.of(a, b), List.of(), 60, 1);

        assertThat(fused).hasSize(1);
        assertThat(fused.get(0).getId()).isEqualTo("a");
    }
}
