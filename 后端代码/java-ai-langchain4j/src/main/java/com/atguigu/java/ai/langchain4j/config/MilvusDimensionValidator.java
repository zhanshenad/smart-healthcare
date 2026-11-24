package com.atguigu.java.ai.langchain4j.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.grpc.FieldSchema;
import io.milvus.grpc.KeyValuePair;
import io.milvus.param.R;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.HasCollectionParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MilvusDimensionValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MilvusDimensionValidator.class);

    private final MilvusServiceClient milvusClient;
    private final MilvusProperties properties;
    private final RagProperties ragProperties;

    public MilvusDimensionValidator(MilvusServiceClient milvusClient,
                                    MilvusProperties properties,
                                    RagProperties ragProperties) {
        this.milvusClient = milvusClient;
        this.properties = properties;
        this.ragProperties = ragProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String collectionName = properties.getCollectionName();
        R<Boolean> hasCollection = milvusClient.hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(collectionName).build());

        if (!isSuccess(hasCollection)) {
            throw new IllegalStateException("查询 Milvus collection 失败: " + collectionName);
        }
        if (!Boolean.TRUE.equals(hasCollection.getData())) {
            throw new IllegalStateException("Milvus collection 不存在: " + collectionName);
        }

        R<DescribeCollectionResponse> describe = milvusClient.describeCollection(
                DescribeCollectionParam.newBuilder().withCollectionName(collectionName).build());

        if (!isSuccess(describe) || describe.getData() == null) {
            throw new IllegalStateException("无法读取 Milvus collection schema: " + collectionName);
        }

        Integer actualDimension = findVectorDimension(describe.getData().getSchema().getFieldsList());
        int expectedDimension = ragProperties.getEmbeddingDimension();

        if (actualDimension == null) {
            throw new IllegalStateException("Milvus collection 中找不到向量维度: " + collectionName);
        }
        if (actualDimension != expectedDimension) {
            throw new IllegalStateException("Embedding 维度与 Milvus collection 不一致: expected="
                    + expectedDimension + ", actual=" + actualDimension);
        }

        log.info("Milvus 向量维度校验通过，collection={}, dimension={}", collectionName, actualDimension);
    }

    private boolean isSuccess(R<?> result) {
        return result != null && result.getStatus() != null
                && result.getStatus() == R.Status.Success.getCode();
    }

    private Integer findVectorDimension(Iterable<FieldSchema> fields) {
        for (FieldSchema field : fields) {
            if (!"dense".equals(field.getName())) {
                continue;
            }
            for (KeyValuePair pair : field.getTypeParamsList()) {
                if ("dim".equals(pair.getKey())) {
                    return Integer.valueOf(pair.getValue());
                }
            }
        }
        return null;
    }
}
