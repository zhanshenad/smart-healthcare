package com.atguigu.java.ai.langchain4j.config;

import com.atguigu.java.ai.langchain4j.memory.MemoryLockRegistry;
import com.atguigu.java.ai.langchain4j.memory.MemoryProperties;
import com.atguigu.java.ai.langchain4j.memory.MongoConversationSummaryStore;
import com.atguigu.java.ai.langchain4j.memory.SummaryChatMemory;
import com.atguigu.java.ai.langchain4j.memory.SummaryService;
import com.atguigu.java.ai.langchain4j.store.MongoChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfig {

    @Bean
    public Tokenizer tokenizer() {
        return new HuggingFaceTokenizer();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProviderXiaozhi(MongoChatMemoryStore messageStore,
                                                        MongoConversationSummaryStore summaryStore,
                                                        SummaryService summaryService,
                                                        MemoryLockRegistry lockRegistry,
                                                        Tokenizer tokenizer,
                                                        MemoryProperties properties) {
        return memoryId -> new SummaryChatMemory(
                memoryId,
                properties.getMaxTokens(),
                properties.getKeepRecentTurns(),
                properties.isSummaryEnabled(),
                tokenizer,
                messageStore,
                summaryStore,
                summaryService,
                lockRegistry);
    }

}
