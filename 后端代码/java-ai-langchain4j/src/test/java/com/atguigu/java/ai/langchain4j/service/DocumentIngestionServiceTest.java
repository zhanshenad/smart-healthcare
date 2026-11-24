package com.atguigu.java.ai.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.atguigu.java.ai.langchain4j.rag.HybridChunk;
import com.atguigu.java.ai.langchain4j.rag.MilvusHybridStore;
import com.atguigu.java.ai.langchain4j.service.chunking.StructureAwareChunker;
import com.atguigu.java.ai.langchain4j.service.parser.MarkdownConversionService;
import com.atguigu.java.ai.langchain4j.service.parser.ParsedDocument;
import com.atguigu.java.ai.langchain4j.vo.KnowledgeIngestResult;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
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
    private MarkdownConversionService conversionService;

    @Mock
    private StructureAwareChunker structureAwareChunker;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private MilvusHybridStore hybridStore;

    @Mock
    private KnowledgeChunkWriter chunkWriter;

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
                conversionService,
                structureAwareChunker,
                embeddingModel,
                hybridStore,
                chunkWriter,
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
        verifyNoInteractions(parseService, conversionService, structureAwareChunker, embeddingModel,
                hybridStore, chunkWriter, chunkMapper);
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
        ParsedDocument parsedDocument = new ParsedDocument(
                "# 标题\n内容", "guide", "MARKDOWN", "HIGH", "markdown");
        when(parseService.parse(file)).thenReturn(parsedDocument);
        when(conversionService.convert(file, parsedDocument)).thenReturn(parsedDocument);
        when(structureAwareChunker.chunk(anyString(), any(Metadata.class)))
                .thenReturn(List.of(TextSegment.from("第一段"), TextSegment.from("第二段")));
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(List.of(
                Embedding.from(new float[]{1, 0, 0}),
                Embedding.from(new float[]{0, 1, 0}))));

        KnowledgeIngestResult result = ingestionService.ingest(file, "guide.md", "神经内科");

        assertThat(result.skipped()).isFalse();
        assertThat(result.chunkCount()).isEqualTo(2);
        assertThat(result.documentId()).isEqualTo(10L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HybridChunk>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(hybridStore).upsert(chunkCaptor.capture());
        assertThat(chunkCaptor.getValue())
                .allSatisfy(chunk -> assertThat(chunk.getId()).hasSize(32));
        verify(chunkWriter).replace(eq(10L), anyString(), anyList());
        verify(documentMapper, times(2)).update(isNull(), any());
    }

    @Test
    void forceShouldReingestUnchangedDocument(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("guide.md");
        Files.writeString(file, "# 标题\n内容", StandardCharsets.UTF_8);

        KnowledgeDocument existing = new KnowledgeDocument();
        existing.setId(1L);
        existing.setDocKey(HashUtils.sha256("guide.md|"));
        existing.setDocHash(HashUtils.sha256(Files.readAllBytes(file)));
        existing.setStatus("READY");
        existing.setChunkCount(2);
        when(documentMapper.selectOne(any())).thenReturn(existing);

        ParsedDocument parsedDocument = new ParsedDocument(
                "# 标题\n内容", "guide", "MARKDOWN", "HIGH", "markdown");
        when(parseService.parse(file)).thenReturn(parsedDocument);
        when(conversionService.convert(file, parsedDocument)).thenReturn(parsedDocument);
        when(structureAwareChunker.chunk(anyString(), any(Metadata.class)))
                .thenReturn(List.of(TextSegment.from("第一段"), TextSegment.from("第二段")));
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(List.of(
                Embedding.from(new float[]{1, 0, 0}),
                Embedding.from(new float[]{0, 1, 0}))));

        KnowledgeIngestResult result = ingestionService.ingest(file, "guide.md", null, true);

        assertThat(result.skipped()).isFalse();
        verify(hybridStore).upsert(anyList());
    }

    @Test
    void childChunkShouldResolveParentId(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("policy.md");
        Files.writeString(file, "# 制度\n内容", StandardCharsets.UTF_8);

        when(documentMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(20L);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocument.class));

        ParsedDocument parsedDocument = new ParsedDocument(
                "# 制度\n内容", "制度", "MARKDOWN", "HIGH", "markdown");
        when(parseService.parse(file)).thenReturn(parsedDocument);
        when(conversionService.convert(file, parsedDocument)).thenReturn(parsedDocument);

        Metadata parentMetadata = new Metadata()
                .put("role", "parent")
                .put("parent_key", "policy-0")
                .put("strategy", "policy_manual_structured")
                .put("outline_path", "制度");
        Metadata childMetadata = new Metadata()
                .put("role", "child")
                .put("parent_key", "policy-0")
                .put("strategy", "policy_manual_structured")
                .put("outline_path", "制度");
        when(structureAwareChunker.chunk(anyString(), any(Metadata.class)))
                .thenReturn(List.of(
                        TextSegment.from("制度父块", parentMetadata),
                        TextSegment.from("制度子块", childMetadata)));
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(List.of(
                Embedding.from(new float[]{0, 1, 0}))));

        ingestionService.ingest(file, "policy.md", "门诊部");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TextSegment>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(chunkWriter).replace(eq(20L), anyString(), chunksCaptor.capture());
        List<TextSegment> chunks = chunksCaptor.getValue();
        String parentId = chunks.get(0).metadata().getString("chunk_id");
        assertThat(chunks.get(0).metadata().getString("parent_id")).isNull();
        assertThat(chunks.get(1).metadata().getString("parent_id")).isEqualTo(parentId);
    }

    @Test
    void blankDocumentShouldFailAfterConversion(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("scan.pdf");
        Files.writeString(file, "content", StandardCharsets.UTF_8);

        when(documentMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(30L);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocument.class));

        ParsedDocument empty = new ParsedDocument("", "scan", "PDF", "LOW", "pdfbox");
        when(parseService.parse(file)).thenReturn(empty);
        when(conversionService.convert(file, empty)).thenReturn(empty);

        assertThatThrownBy(() -> ingestionService.ingest(file, "scan.pdf", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("文档解析结果为空");

        verifyNoInteractions(structureAwareChunker, embeddingModel, hybridStore, chunkWriter);
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
        verifyNoInteractions(conversionService, structureAwareChunker, embeddingModel,
                hybridStore, chunkWriter, chunkMapper);
    }
}
