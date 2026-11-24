package com.atguigu.java.ai.langchain4j.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class MilvusConfig {

    @Bean(destroyMethod = "close")
    public MilvusServiceClient milvusServiceClient(MilvusProperties properties) {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(properties.getHost())
                .withPort(properties.getPort())
                .withDatabaseName(properties.getDatabaseName());

        if (StringUtils.hasText(properties.getUsername())) {
            builder.withAuthorization(properties.getUsername(), properties.getPassword());
        }

        return new MilvusServiceClient(builder.build());
    }
}
