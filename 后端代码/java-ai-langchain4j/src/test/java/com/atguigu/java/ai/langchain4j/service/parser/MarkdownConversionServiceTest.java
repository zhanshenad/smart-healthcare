package com.atguigu.java.ai.langchain4j.service.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.atguigu.java.ai.langchain4j.config.RagProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkdownConversionServiceTest {

    @Mock
    private MineruClient mineruClient;

    @Test
    void shouldKeepMarkdownUnchanged() {
        RagProperties properties = new RagProperties();
        MarkdownConversionService service = new MarkdownConversionService(mineruClient, new MarkdownNormalizer(), properties);
        ParsedDocument parsed = new ParsedDocument(
                "# 标题\n内容", "标题", "MARKDOWN", "HIGH", "markdown");

        ParsedDocument converted = service.convert(Path.of("test.md"), parsed);

        assertThat(converted).isSameAs(parsed);
    }

    @Test
    void shouldConvertPlainTextToMarkdown() {
        RagProperties properties = new RagProperties();
        MarkdownConversionService service = new MarkdownConversionService(mineruClient, new MarkdownNormalizer(), properties);
        ParsedDocument parsed = new ParsedDocument(
                "第一段\r\n\r\n第二段", "说明", "TEXT", "LOW", "text");

        ParsedDocument converted = service.convert(Path.of("test.txt"), parsed);

        assertThat(converted.markdown()).startsWith("# 说明");
        assertThat(converted.markdown()).contains("第一段").contains("第二段");
        assertThat(converted.parserName()).isEqualTo("text+text2md");
    }

    @Test
    void shouldReturnBlankWhenNoTextAndMineruDisabled() {
        RagProperties properties = new RagProperties();
        MarkdownConversionService service = new MarkdownConversionService(
                mineruClient, new MarkdownNormalizer(), properties);
        ParsedDocument parsed = new ParsedDocument("", "empty", "PDF", "LOW", "pdfbox");

        ParsedDocument converted = service.convert(Path.of("empty.pdf"), parsed);

        assertThat(converted.markdown()).isBlank();
    }

    @Test
    void shouldUseMineruForPdfWhenEnabled(@TempDir Path tempDir) throws IOException {
        Path file = Files.createFile(tempDir.resolve("guide.pdf"));
        RagProperties properties = new RagProperties();
        properties.getMineru().setEnabled(true);
        when(mineruClient.parseMarkdown(file)).thenReturn("# MinerU 结果\n内容");

        MarkdownConversionService service = new MarkdownConversionService(mineruClient, new MarkdownNormalizer(), properties);
        ParsedDocument parsed = new ParsedDocument(
                "普通 PDF 文本", "guide", "PDF", "LOW", "pdfbox");

        ParsedDocument converted = service.convert(file, parsed);

        assertThat(converted.markdown()).contains("MinerU");
        assertThat(converted.structureQuality()).isEqualTo("HIGH");
        assertThat(converted.parserName()).isEqualTo("mineru");
    }
}
