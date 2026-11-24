package com.atguigu.java.ai.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.service.parser.MarkdownFileParser;
import com.atguigu.java.ai.langchain4j.service.parser.MarkdownNormalizer;
import com.atguigu.java.ai.langchain4j.service.parser.ParsedDocument;
import com.atguigu.java.ai.langchain4j.service.parser.PdfFileParser;
import com.atguigu.java.ai.langchain4j.service.parser.TextFileParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentParseServiceTest {

    private final DocumentParseService parseService = new DocumentParseService(
            List.of(new MarkdownFileParser(), new TextFileParser(), new PdfFileParser()),
            new MarkdownNormalizer());

    @Test
    void markdownShouldBeNormalized(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("guide.md");
        Files.writeString(file, "﻿#标题\r\n\r\n\r\n正文  \r\n", StandardCharsets.UTF_8);

        ParsedDocument parsed = parseService.parse(file);

        assertThat(parsed.docType()).isEqualTo("MARKDOWN");
        assertThat(parsed.structureQuality()).isEqualTo("HIGH");
        assertThat(parsed.title()).isEqualTo("guide");
        assertThat(parsed.markdown()).startsWith("# 标题");
        assertThat(parsed.markdown()).doesNotContain("\r");
        assertThat(parsed.markdown()).doesNotContain("\n\n\n");
    }

    @Test
    void unsupportedTypeShouldThrow(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("guide.docx");
        Files.writeString(file, "content", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parseService.parse(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不支持");
    }
}
