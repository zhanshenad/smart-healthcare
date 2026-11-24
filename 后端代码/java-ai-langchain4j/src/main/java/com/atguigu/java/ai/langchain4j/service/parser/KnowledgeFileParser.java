package com.atguigu.java.ai.langchain4j.service.parser;

import java.io.IOException;
import java.nio.file.Path;

public interface KnowledgeFileParser {

    boolean supports(String extension);

    ParsedDocument parse(Path file) throws IOException;

    default String titleOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
