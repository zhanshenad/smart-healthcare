package com.atguigu.java.ai.langchain4j.service.chunking;

import java.util.List;

public record MarkdownSection(int index,
                              int level,
                              String heading,
                              String outlinePath,
                              List<String> lines) {

    public String text() {
        return String.join("\n", lines).strip();
    }

    public boolean isBlank() {
        return text().isBlank();
    }
}
