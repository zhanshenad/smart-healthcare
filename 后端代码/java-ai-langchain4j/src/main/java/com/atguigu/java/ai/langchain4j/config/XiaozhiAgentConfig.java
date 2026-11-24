package com.atguigu.java.ai.langchain4j.config;

import com.atguigu.java.ai.langchain4j.rag.MedicalContentInjector;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XiaozhiAgentConfig {

    @Bean
    public RetrievalAugmentor medicalRetrievalAugmentor(ContentRetriever contentRetrieverXiaozhiMilvus,
                                                        MedicalContentInjector contentInjector) {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetrieverXiaozhiMilvus)
                .contentAggregator(new DefaultContentAggregator())
                .contentInjector(contentInjector)
                .build();
    }
}
