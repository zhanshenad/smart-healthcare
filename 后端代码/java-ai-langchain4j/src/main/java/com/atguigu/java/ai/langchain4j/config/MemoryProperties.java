package com.atguigu.java.ai.langchain4j.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "xiaozhi.memory")
public class MemoryProperties {

    private int maxTokens = 200000;

    private int keepRecentTurns = 8;

    private boolean summaryEnabled = true;
}
