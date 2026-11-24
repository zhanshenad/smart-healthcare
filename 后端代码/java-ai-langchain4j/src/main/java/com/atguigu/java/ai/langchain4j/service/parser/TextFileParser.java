package com.atguigu.java.ai.langchain4j.service.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TextFileParser implements KnowledgeFileParser {

    @Override
    public boolean supports(String extension) {
        return "txt".equals(extension.toLowerCase(Locale.ROOT));
    }

    @Override
    public ParsedDocument parse(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return new ParsedDocument(text, titleOf(file), "TEXT", "MEDIUM", "text");
    }
}
