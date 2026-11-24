package com.atguigu.java.ai.langchain4j.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class EmbeddingStoreConfig {

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(MilvusServiceClient milvusClient,
                                                      MilvusProperties milvusProperties,
                                                      RagProperties ragProperties) {
        int dimension = ragProperties.getEmbeddingDimension();
        log.info("初始化 Milvus 向量库，collection={}, dimension={}",
                milvusProperties.getCollectionName(), dimension);

        return MilvusEmbeddingStore.builder()
                .milvusClient(milvusClient)
                .collectionName(milvusProperties.getCollectionName())
                .dimension(dimension)
                .indexType(IndexType.HNSW)
                .metricType(MetricType.COSINE)
                .consistencyLevel(ConsistencyLevelEnum.BOUNDED)
                .autoFlushOnInsert(true)
                .build();
    }
}
