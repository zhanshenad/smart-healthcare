package com.atguigu.java.ai.langchain4j.service.chunking;

public record ChunkDraft(String text,
                         String strategy,
                         String role,
                         String outlinePath,
                         String parentKey) {
}
