package com.atguigu.java.ai.langchain4j.service.parser;

public record ParsedDocument(
        String markdown,
        String title,
        String docType,
        String structureQuality,
        String parserName) {

    public ParsedDocument withMarkdown(String normalizedMarkdown) {
        return new ParsedDocument(normalizedMarkdown, title, docType, structureQuality, parserName);
    }
}
