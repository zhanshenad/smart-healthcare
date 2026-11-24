package com.atguigu.java.ai.langchain4j.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xiaozhi.rag.milvus")
public class MilvusProperties {

    private String host = "localhost";

    private int port = 19530;

    private String databaseName = "default";

    private String collectionName = "smart_healthcare_chunks";

    private String username;

    private String password;
}
