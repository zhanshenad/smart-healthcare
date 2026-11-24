package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.service.parser.KnowledgeFileParser;
import com.atguigu.java.ai.langchain4j.service.parser.MarkdownNormalizer;
import com.atguigu.java.ai.langchain4j.service.parser.ParsedDocument;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class DocumentParseService {

    private final List<KnowledgeFileParser> parsers;
    private final MarkdownNormalizer normalizer;

    public DocumentParseService(List<KnowledgeFileParser> parsers, MarkdownNormalizer normalizer) {
        this.parsers = parsers;
        this.normalizer = normalizer;
    }

    public ParsedDocument parse(Path file) {
        String extension = extensionOf(file);
        KnowledgeFileParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(extension))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("不支持的文件类型: " + extension));

        try {
            ParsedDocument parsed = parser.parse(file);
            String markdown = normalizer.normalize(parsed.markdown());
            if (markdown.isBlank()) {
                throw ApiException.badRequest("文档解析结果为空，可能是扫描件或加密文件");
            }
            return parsed.withMarkdown(markdown);
        } catch (IOException e) {
            throw ApiException.badRequest("文档解析失败: " + e.getMessage());
        }
    }

    private String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
