package com.atguigu.java.ai.langchain4j.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.atguigu.java.ai.langchain4j.entity.KnowledgeChunk;
import com.atguigu.java.ai.langchain4j.mapper.KnowledgeChunkMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParentChunkResolverTest {

    @Mock
    private KnowledgeChunkMapper chunkMapper;

    @Test
    void shouldLoadParentContent() {
        ParentChunkResolver resolver = new ParentChunkResolver(chunkMapper);
        RetrievalHit child = RetrievalHit.builder()
                .id("child")
                .parentId("parent")
                .build();
        KnowledgeChunk parent = new KnowledgeChunk();
        parent.setChunkId("parent");
        parent.setContent("完整父块内容");
        when(chunkMapper.selectList(any())).thenReturn(List.of(parent));

        Map<String, String> parents = resolver.resolve(List.of(child));

        assertThat(parents).containsEntry("parent", "完整父块内容");
    }

    @Test
    void shouldSkipQueryWhenNoParentId() {
        ParentChunkResolver resolver = new ParentChunkResolver(chunkMapper);
        RetrievalHit hit = RetrievalHit.builder().id("normal").build();

        Map<String, String> parents = resolver.resolve(List.of(hit));

        assertThat(parents).isEmpty();
        verifyNoInteractions(chunkMapper);
    }
}
