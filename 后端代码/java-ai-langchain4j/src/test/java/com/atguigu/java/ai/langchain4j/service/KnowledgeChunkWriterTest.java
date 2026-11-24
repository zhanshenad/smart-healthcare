package com.atguigu.java.ai.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.atguigu.java.ai.langchain4j.entity.KnowledgeChunk;
import com.atguigu.java.ai.langchain4j.mapper.KnowledgeChunkMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeChunkWriterTest {

    @Mock
    private KnowledgeChunkMapper chunkMapper;

    @Test
    void shouldReplaceDocumentChunks() {
        KnowledgeChunkWriter writer = new KnowledgeChunkWriter(chunkMapper);
        TextSegment first = TextSegment.from("第一段", new Metadata()
                .put("chunk_id", "chunk-1")
                .put("chunk_index", 0)
                .put("content_hash", "hash-1")
                .put("outline_path", "制度 > 总则")
                .put("role", "child")
                .put("strategy", "policy_manual_structured"));
        TextSegment second = TextSegment.from("第二段", new Metadata()
                .put("chunk_id", "chunk-2")
                .put("chunk_index", 1)
                .put("content_hash", "hash-2")
                .put("parent_id", "chunk-1"));

        writer.replace(10L, "doc-key", List.of(first, second));

        verify(chunkMapper).delete(any());
        ArgumentCaptor<KnowledgeChunk> captor = ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(KnowledgeChunk::getChunkId)
                .containsExactly("chunk-1", "chunk-2");
        assertThat(captor.getAllValues())
                .allSatisfy(row -> assertThat(row.getDocumentId()).isEqualTo(10L));
        assertThat(captor.getAllValues().get(0).getOutlinePath()).isEqualTo("制度 > 总则");
        assertThat(captor.getAllValues().get(0).getRole()).isEqualTo("child");
        assertThat(captor.getAllValues().get(0).getStrategy()).isEqualTo("policy_manual_structured");
        assertThat(captor.getAllValues().get(1).getParentId()).isEqualTo("chunk-1");
    }
}
