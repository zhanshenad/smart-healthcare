package com.atguigu.java.ai.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.common.HashUtils;
import com.atguigu.java.ai.langchain4j.config.RagProperties;
import com.atguigu.java.ai.langchain4j.entity.KnowledgeChunk;
import com.atguigu.java.ai.langchain4j.entity.KnowledgeDocument;
import com.atguigu.java.ai.langchain4j.mapper.KnowledgeChunkMapper;
import com.atguigu.java.ai.langchain4j.mapper.KnowledgeDocumentMapper;
import com.atguigu.java.ai.langchain4j.service.parser.ParsedDocument;
import com.atguigu.java.ai.langchain4j.vo.KnowledgeIngestResult;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private DocumentParseService parseService;

    @Mock
    private DocumentSplitter documentSplitter;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    private KnowledgeDocumentMapper documentMapper;

    @Mock
    private KnowledgeChunkMapper chunkMapper;

    private DocumentIngestionService ingestionService;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeChunk.class);
    }

    @BeforeEach
    void setUp() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEmbeddingDimension(3);
        ingestionService = new DocumentIngestionService(
                parseService,
                documentSplitter,
                embeddingModel,
                embeddingStore,
                documentMapper,
                chunkMapper,
                ragProperties);
    }

    @Test
    void unchangedDocumentShouldBeSkipped(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("guide.md");
        Files.writeString(file, "# 标题\n内容", StandardCharsets.UTF_8);

        KnowledgeDocument existing = new KnowledgeDocument();
        existing.setId(1L);
        existing.setDocKey(HashUtils.sha256("guide.md|"));
        existing.setDocHash(HashUtils.sha256(Files.readAllBytes(file)));
        existing.setStatus("READY");
        existing.setChunkCount(2);
        existing.setTitle("guide");
        when(documentMapper.selectOne(any())).thenReturn(existing);

        KnowledgeIngestResult result = ingestionService.ingest(file, "guide.md", null);

        assertThat(result.skipped()).isTrue();
        assertThat(result.chunkCount()).isEqualTo(2);
        verify(documentMapper, never()).insert(any(KnowledgeDocument.class));
        verifyNoInteractions(parseService, documentSplitter, embeddingModel, embeddingStore, chunkMapper);
    }

    @Test
    void newDocumentShouldBeChunkedAndStored(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("guide.md");
        Files.writeString(file, "# 标题\n内容", StandardCharsets.UTF_8);

        when(documentMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(10L);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocument.class));
        when(parseService.parse(file))
                .thenReturn(new ParsedDocument("# 标题\n内容", "guide", "MARKDOWN", "HIGH", "markdown"));
        when(documentSplitter.split(any(Document.class)))
                .thenReturn(List.of(TextSegment.from("第一段"), TextSegment.from("第二段")));
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(List.of(
                Embedding.from(new float[]{1, 0, 0}),
                Embedding.from(new float[]{0, 1, 0}))));

        KnowledgeIngestResult result = ingestionService.ingest(file, "guide.md", "神经内科");

        assertThat(result.skipped()).isFalse();
        assertThat(result.chunkCount()).isEqualTo(2);
        assertThat(result.documentId()).isEqualTo(10L);
        ArgumentCaptor<KnowledgeChunk> chunkCaptor = ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkMapper, times(2)).insert(chunkCaptor.capture());
        assertThat(chunkCaptor.getAllValues())
                .allSatisfy(chunk -> assertThat(chunk.getChunkId()).hasSize(32));
        verify(embeddingStore).addAll(anyList(), anyList(), anyList());
        verify(documentMapper, times(2)).update(isNull(), any());
    }

    @Test
    void parseFailureShouldMarkDocumentFailed(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("guide.docx");
        Files.writeString(file, "content", StandardCharsets.UTF_8);

        when(documentMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(11L);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocument.class));
        when(parseService.parse(file))
                .thenThrow(ApiException.badRequest("不支持的文件类型: docx"));

        assertThatThrownBy(() -> ingestionService.ingest(file, "guide.docx", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不支持");

        ArgumentCaptor<KnowledgeDocument> documentCaptor =
                ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentMapper).insert(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(documentCaptor.getValue().getErrorMessage()).contains("不支持");
        verifyNoInteractions(documentSplitter, embeddingModel, embeddingStore, chunkMapper);
    }
}
