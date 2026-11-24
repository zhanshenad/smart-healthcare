package com.atguigu.java.ai.langchain4j.rag;

import com.atguigu.java.ai.langchain4j.config.MilvusProperties;
import com.atguigu.java.ai.langchain4j.config.RagProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MilvusHybridStore {

    private static final Logger log = LoggerFactory.getLogger(MilvusHybridStore.class);

    private static final int MIN_HNSW_EF = 64;

    private static final List<String> OUTPUT_FIELDS = List.of(
            "text", "doc_key", "doc_hash", "source", "title", "doc_type",
            "department", "chunk_index", "content_hash", "structure_quality", "parser",
            "outline_path", "parent_id", "role", "strategy");

    private final MilvusClientV2 client;
    private final MilvusProperties milvusProperties;
    private final RagProperties ragProperties;

    public MilvusHybridStore(MilvusClientV2 milvusClient,
                             MilvusProperties milvusProperties,
                             RagProperties ragProperties) {
        this.client = milvusClient;
        this.milvusProperties = milvusProperties;
        this.ragProperties = ragProperties;
    }

    @PostConstruct
    public void init() {
        ensureCollection();
    }

    public void upsert(List<HybridChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        List<JsonObject> data = chunks.stream().map(this::toJson).toList();
        client.upsert(UpsertReq.builder()
                .collectionName(collectionName())
                .data(data)
                .build());
        log.info("Milvus 写入完成，collection={}, chunks={}", collectionName(), chunks.size());
    }

    public List<RetrievalHit> searchDense(float[] vector, int topK) {
        SearchReq request = SearchReq.builder()
                .collectionName(collectionName())
                .annsField("dense")
                .metricType(IndexParam.MetricType.COSINE)
                .topK(topK)
                .filter("role != \"parent\"")
                .data(List.of(new FloatVec(vector)))
                .outputFields(OUTPUT_FIELDS)
                .searchParams(Map.of("ef", Math.max(MIN_HNSW_EF, topK)))
                .consistencyLevel(ConsistencyLevel.STRONG)
                .build();
        return toHits(client.search(request), true);
    }

    public List<RetrievalHit> searchSparse(String queryText, int topK) {
        SearchReq request = SearchReq.builder()
                .collectionName(collectionName())
                .annsField("sparse")
                .metricType(IndexParam.MetricType.BM25)
                .topK(topK)
                .filter("role != \"parent\"")
                .data(List.of(new EmbeddedText(queryText)))
                .outputFields(OUTPUT_FIELDS)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .build();
        return toHits(client.search(request), false);
    }

    public void deleteByIds(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }

        client.delete(DeleteReq.builder()
                .collectionName(collectionName())
                .ids(new ArrayList<>(chunkIds))
                .build());
        log.info("Milvus 删除完成，collection={}, chunks={}", collectionName(), chunkIds.size());
    }

    private void ensureCollection() {
        String collectionName = collectionName();
        Boolean exists = client.hasCollection(HasCollectionReq.builder()
                .collectionName(collectionName)
                .build());

        if (Boolean.TRUE.equals(exists)) {
            DescribeCollectionResp describe = client.describeCollection(DescribeCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
            List<String> fieldNames = describe.getFieldNames();
            if (fieldNames == null || !fieldNames.containsAll(List.of(
                    "text", "dense", "sparse", "outline_path", "parent_id", "role", "strategy"))) {
                throw new IllegalStateException("Milvus collection 不支持混合检索，请删除后重建: "
                        + collectionName);
            }
            loadCollection();
            log.info("Milvus 混合检索集合已就绪，collection={}, dimension={}",
                    collectionName, ragProperties.getEmbeddingDimension());
            return;
        }

        createCollection();
        loadCollection();
        log.info("Milvus 混合检索集合创建完成，collection={}, dimension={}",
                collectionName, ragProperties.getEmbeddingDimension());
    }

    private void createCollection() {
        int dimension = ragProperties.getEmbeddingDimension();

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(List.of(
                        field("id", DataType.VarChar).maxLength(64).isPrimaryKey(true).autoID(false).build(),
                        field("text", DataType.VarChar).maxLength(65535)
                                .enableAnalyzer(true)
                                .analyzerParams(Map.of("type", "chinese"))
                                .build(),
                        field("dense", DataType.FloatVector).dimension(dimension).build(),
                        field("sparse", DataType.SparseFloatVector).build(),
                        field("doc_key", DataType.VarChar).maxLength(64).build(),
                        field("doc_hash", DataType.VarChar).maxLength(64).build(),
                        field("source", DataType.VarChar).maxLength(512).build(),
                        field("title", DataType.VarChar).maxLength(512).build(),
                        field("doc_type", DataType.VarChar).maxLength(32).build(),
                        field("department", DataType.VarChar).maxLength(128).build(),
                        field("chunk_index", DataType.Int32).build(),
                        field("content_hash", DataType.VarChar).maxLength(64).build(),
                        field("structure_quality", DataType.VarChar).maxLength(32).build(),
                        field("parser", DataType.VarChar).maxLength(64).build(),
                        field("outline_path", DataType.VarChar).maxLength(1024).build(),
                        field("parent_id", DataType.VarChar).maxLength(64).build(),
                        field("role", DataType.VarChar).maxLength(32).build(),
                        field("strategy", DataType.VarChar).maxLength(64).build()))
                .functionList(List.of(CreateCollectionReq.Function.builder()
                        .name("text_bm25")
                        .functionType(FunctionType.BM25)
                        .inputFieldNames(List.of("text"))
                        .outputFieldNames(List.of("sparse"))
                        .build()))
                .build();

        IndexParam denseIndex = IndexParam.builder()
                .fieldName("dense")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("M", 16, "efConstruction", 200))
                .build();

        IndexParam sparseIndex = IndexParam.builder()
                .fieldName("sparse")
                .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                .metricType(IndexParam.MetricType.BM25)
                .build();

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName())
                .collectionSchema(schema)
                .indexParams(List.of(denseIndex, sparseIndex))
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .build());
    }

    private CreateCollectionReq.FieldSchema.FieldSchemaBuilder<?, ?> field(String name, DataType dataType) {
        return CreateCollectionReq.FieldSchema.builder()
                .name(name)
                .dataType(dataType);
    }

    private void loadCollection() {
        client.loadCollection(LoadCollectionReq.builder()
                .collectionName(collectionName())
                .build());
    }

    private JsonObject toJson(HybridChunk chunk) {
        JsonObject object = new JsonObject();
        object.addProperty("id", chunk.getId());
        object.addProperty("text", chunk.getText());

        JsonArray dense = new JsonArray();
        for (float value : chunk.getDense()) {
            dense.add(value);
        }
        object.add("dense", dense);

        object.addProperty("doc_key", chunk.getDocKey());
        object.addProperty("doc_hash", chunk.getDocHash());
        object.addProperty("source", truncate(chunk.getSource(), 512));
        object.addProperty("title", truncate(chunk.getTitle(), 512));
        object.addProperty("doc_type", truncate(chunk.getDocType(), 32));
        object.addProperty("department", truncate(chunk.getDepartment(), 128));
        object.addProperty("chunk_index", chunk.getChunkIndex());
        object.addProperty("content_hash", chunk.getContentHash());
        object.addProperty("structure_quality", truncate(chunk.getStructureQuality(), 32));
        object.addProperty("parser", truncate(chunk.getParser(), 64));
        object.addProperty("outline_path", truncate(chunk.getOutlinePath(), 1024));
        object.addProperty("parent_id", truncate(chunk.getParentId(), 64));
        object.addProperty("role", truncate(chunk.getRole(), 32));
        object.addProperty("strategy", truncate(chunk.getStrategy(), 64));
        return object;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private List<RetrievalHit> toHits(SearchResp response, boolean dense) {
        if (response == null || response.getSearchResults() == null
                || response.getSearchResults().isEmpty()) {
            return List.of();
        }

        List<RetrievalHit> hits = new ArrayList<>();
        for (SearchResp.SearchResult result : response.getSearchResults().get(0)) {
            Map<String, Object> entity = result.getEntity();
            RetrievalHit hit = RetrievalHit.builder()
                    .id(String.valueOf(result.getId()))
                    .text(asString(entity.get("text")))
                    .docKey(asString(entity.get("doc_key")))
                    .docHash(asString(entity.get("doc_hash")))
                    .source(asString(entity.get("source")))
                    .title(asString(entity.get("title")))
                    .docType(asString(entity.get("doc_type")))
                    .department(asString(entity.get("department")))
                    .chunkIndex(asInt(entity.get("chunk_index")))
                    .contentHash(asString(entity.get("content_hash")))
                    .structureQuality(asString(entity.get("structure_quality")))
                    .parser(asString(entity.get("parser")))
                    .outlinePath(asString(entity.get("outline_path")))
                    .parentId(asString(entity.get("parent_id")))
                    .role(asString(entity.get("role")))
                    .strategy(asString(entity.get("strategy")))
                    .build();

            double score = result.getScore() == null ? 0D : result.getScore();
            if (dense) {
                hit.setDenseScore(score);
            } else {
                hit.setSparseScore(score);
            }
            hits.add(hit);
        }
        return hits;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String collectionName() {
        return milvusProperties.getCollectionName();
    }
}
