package com.atguigu.java.ai.langchain4j.config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    // 文档切片配置
    @Bean
    public DocumentSplitter documentSplitter() {
        return DocumentSplitters.recursive(500, 80);
    }
}
