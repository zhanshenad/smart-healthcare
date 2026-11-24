package com.atguigu.java.ai.langchain4j.vo;

import java.time.LocalDateTime;

public record KnowledgeDocumentInfo(
        Long id,
        String title,
        String source,
        String docType,
        String department,
        String status,
        int chunkCount,
        String errorMessage,
        LocalDateTime updatedAt) {
}
