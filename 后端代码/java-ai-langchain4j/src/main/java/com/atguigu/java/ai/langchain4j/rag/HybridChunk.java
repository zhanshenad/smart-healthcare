package com.atguigu.java.ai.langchain4j.rag;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HybridChunk {

    String id;

    String text;

    float[] dense;

    String docKey;

    String docHash;

    String source;

    String title;

    String docType;

    String department;

    int chunkIndex;

    String contentHash;

    String structureQuality;

    String parser;

    String outlinePath;

    String parentId;

    String role;

    String strategy;
}
