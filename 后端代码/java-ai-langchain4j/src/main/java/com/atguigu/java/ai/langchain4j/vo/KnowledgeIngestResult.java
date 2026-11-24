package com.atguigu.java.ai.langchain4j.vo;

public record KnowledgeIngestResult(
        Long documentId,
        String docKey,
        String title,
        int chunkCount,
        boolean skipped,
        String status) {
}
