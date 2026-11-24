package com.atguigu.java.ai.langchain4j.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.atguigu.java.ai.langchain4j.config.RagProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RerankServiceTest {

    @Mock
    private RerankClient rerankClient;

    private RagProperties ragProperties;

    private RerankService rerankService;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.getRerank().setApiKey("test-key");
        rerankService = new RerankService(rerankClient, ragProperties);
    }

    @Test
    void shouldSortByRerankScore() {
        RetrievalHit first = RetrievalHit.builder().id("1").text("第一段").build();
        RetrievalHit second = RetrievalHit.builder().id("2").text("第二段").build();
        when(rerankClient.score(eq("头痛"), anyList())).thenReturn(List.of(0.2, 0.9));

        List<RetrievalHit> reranked = rerankService.rerank("头痛", List.of(first, second));

        assertThat(reranked).extracting(RetrievalHit::getId).containsExactly("2", "1");
        assertThat(reranked.get(0).isReranked()).isTrue();
        assertThat(reranked.get(0).getRerankScore()).isEqualTo(0.9);
    }

    @Test
    void shouldFallbackToRrfOrderWhenRerankFails() {
        RetrievalHit first = RetrievalHit.builder().id("1").text("第一段").build();
        RetrievalHit second = RetrievalHit.builder().id("2").text("第二段").build();
        when(rerankClient.score(eq("头痛"), anyList())).thenThrow(new IllegalStateException("timeout"));

        List<RetrievalHit> reranked = rerankService.rerank("头痛", List.of(first, second));

        assertThat(reranked).extracting(RetrievalHit::getId).containsExactly("1", "2");
        assertThat(reranked.get(0).isReranked()).isFalse();
    }
}
