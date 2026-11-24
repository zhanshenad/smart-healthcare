package com.atguigu.java.ai.langchain4j.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atguigu.java.ai.langchain4j.config.RagProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HybridContentRetrieverTest {

    @Test
    void shouldExpandChildChunkWithParentContent() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        MilvusHybridStore hybridStore = mock(MilvusHybridStore.class);
        RerankService rerankService = mock(RerankService.class);
        ParentChunkResolver parentChunkResolver = mock(ParentChunkResolver.class);
        RagProperties ragProperties = new RagProperties();

        float[] vector = new float[1024];
        vector[0] = 1F;
        when(embeddingModel.embed("头痛")).thenReturn(Response.from(Embedding.from(vector)));

        RetrievalHit child = RetrievalHit.builder()
                .id("child")
                .text("子块内容")
                .source("神经内科指南.md")
                .outlinePath("神经内科 > 挂号")
                .parentId("parent")
                .role("child")
                .strategy("policy_manual_structured")
                .chunkIndex(0)
                .build();
        when(hybridStore.searchDense(any(), anyInt())).thenReturn(List.of(child));
        when(hybridStore.searchSparse(anyString(), anyInt())).thenReturn(List.of());
        when(rerankService.rerank(anyString(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(parentChunkResolver.resolve(anyList()))
                .thenReturn(Map.of("parent", "父块完整内容"));

        HybridContentRetriever retriever = new HybridContentRetriever(
                embeddingModel, hybridStore, new HybridRrfFuser(), rerankService,
                new BoostService(ragProperties), parentChunkResolver, ragProperties);

        List<Content> contents = retriever.retrieve(Query.from("头痛"));

        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).textSegment().text())
                .contains("[来源1]")
                .contains("神经内科 > 挂号")
                .contains("父块完整内容");
        assertThat(contents.get(0).textSegment().metadata().getString("parent_id"))
                .isEqualTo("parent");
    }
}
