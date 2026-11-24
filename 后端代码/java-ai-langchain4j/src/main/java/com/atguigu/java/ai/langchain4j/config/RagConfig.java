package com.atguigu.java.ai.langchain4j.config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public DocumentSplitter documentSplitter(RagProperties properties) {
        return DocumentSplitters.recursive(properties.getChunkSize(), properties.getChunkOverlap());
    }
}
