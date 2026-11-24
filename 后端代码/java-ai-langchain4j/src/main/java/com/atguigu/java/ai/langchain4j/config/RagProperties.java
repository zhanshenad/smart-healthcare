package com.atguigu.java.ai.langchain4j.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xiaozhi.rag")
public class RagProperties {

    private int embeddingDimension = 1024;

    private int chunkSize = 500;

    private int chunkOverlap = 80;
}
