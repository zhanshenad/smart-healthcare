package com.atguigu.java.ai.langchain4j.service.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MarkdownFileParser implements KnowledgeFileParser {

    @Override
    public boolean supports(String extension) {
        String ext = extension.toLowerCase(Locale.ROOT);
        return "md".equals(ext) || "markdown".equals(ext);
    }

    @Override
    public ParsedDocument parse(Path file) throws IOException {
        String markdown = Files.readString(file, StandardCharsets.UTF_8);
        return new ParsedDocument(markdown, titleOf(file), "MARKDOWN", "HIGH", "markdown");
    }
}
