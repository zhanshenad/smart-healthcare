package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.common.HashUtils;
import com.atguigu.java.ai.langchain4j.config.RagProperties;
import com.atguigu.java.ai.langchain4j.entity.KnowledgeChunk;
import com.atguigu.java.ai.langchain4j.entity.KnowledgeDocument;
import com.atguigu.java.ai.langchain4j.mapper.KnowledgeChunkMapper;
import com.atguigu.java.ai.langchain4j.mapper.KnowledgeDocumentMapper;
import com.atguigu.java.ai.langchain4j.service.parser.ParsedDocument;
import com.atguigu.java.ai.langchain4j.vo.KnowledgeIngestResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";

    // LangChain4j 自建 Milvus 集合的主键是 VarChar(36)，这里留 4 位余量
    private static final int MILVUS_ID_MAX_LENGTH = 32;

    private final DocumentParseService parseService;
    private final DocumentSplitter documentSplitter;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final RagProperties ragProperties;

    public DocumentIngestionService(DocumentParseService parseService,
                                    DocumentSplitter documentSplitter,
                                    EmbeddingModel embeddingModel,
                                    EmbeddingStore<TextSegment> embeddingStore,
                                    KnowledgeDocumentMapper documentMapper,
                                    KnowledgeChunkMapper chunkMapper,
                                    RagProperties ragProperties) {
        this.parseService = parseService;
        this.documentSplitter = documentSplitter;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.ragProperties = ragProperties;
    }

    public KnowledgeIngestResult ingest(Path file, String originalName, String department) {
        String source = normalizeSource(originalName);
        String docKey = HashUtils.sha256(source + "|" + (department == null ? "" : department));
        String docHash = hashFile(file);

        KnowledgeDocument document = findByDocKey(docKey);
        if (document != null
                && docHash.equals(document.getDocHash())
                && STATUS_READY.equals(document.getStatus())) {
            log.info("文档未变化，跳过入库，docKey={}, source={}", docKey, source);
            return toResult(document, true);
        }

        document = savePendingDocument(document, docKey, source, department, docHash,
                source, docTypeOf(file));

        List<String> chunkIds = List.of();
        try {
            ParsedDocument parsed = parseService.parse(file);
            updateParsedDocument(document, parsed);

            List<TextSegment> chunks = split(parsed, docKey, source, department, docHash);
            if (chunks.isEmpty()) {
                throw ApiException.badRequest("文档切片为空，请检查文档内容");
            }

            chunkIds = chunks.stream()
                    .map(chunk -> chunk.metadata().getString("chunk_id"))
                    .toList();

            List<Embedding> embeddings = embed(chunks);
            removeOldChunks(document);
            insertChunkRows(document, docKey, chunks);
            embeddingStore.addAll(chunkIds, embeddings, chunks);
            markReady(document, chunks.size());
            log.info("文档入库完成，docKey={}, chunks={}", docKey, chunks.size());
            return toResult(document, false);
        } catch (ApiException e) {
            markFailed(document, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("文档入库失败，docKey={}", docKey, e);
            cleanupNewData(chunkIds);
            markFailed(document, e.getMessage());
            throw new ApiException(500, "文档入库失败: " + e.getMessage());
        }
    }

    public List<KnowledgeDocument> listDocuments() {
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .orderByDesc(KnowledgeDocument::getUpdatedAt));
    }

    private KnowledgeDocument savePendingDocument(KnowledgeDocument document,
                                                   String docKey,
                                                   String source,
                                                   String department,
                                                   String docHash,
                                                   String title,
                                                   String docType) {
        if (document == null) {
            document = new KnowledgeDocument();
            document.setDocKey(docKey);
            document.setSource(source);
            document.setTitle(title);
            document.setDocType(docType);
            document.setDepartment(department);
            document.setDocHash(docHash);
            document.setStatus(STATUS_PENDING);
            document.setChunkCount(0);
            document.setErrorMessage("");
            documentMapper.insert(document);
            return document;
        }

        document.setTitle(title);
        document.setDocType(docType);
        document.setDepartment(department);
        document.setDocHash(docHash);
        document.setStatus(STATUS_PENDING);
        document.setErrorMessage("");
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, document.getId())
                .set(KnowledgeDocument::getTitle, title)
                .set(KnowledgeDocument::getDocType, docType)
                .set(KnowledgeDocument::getDepartment, department)
                .set(KnowledgeDocument::getDocHash, docHash)
                .set(KnowledgeDocument::getStatus, STATUS_PENDING)
                .set(KnowledgeDocument::getErrorMessage, ""));
        return document;
    }

    private void updateParsedDocument(KnowledgeDocument document, ParsedDocument parsed) {
        document.setTitle(parsed.title());
        document.setDocType(parsed.docType());
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, document.getId())
                .set(KnowledgeDocument::getTitle, parsed.title())
                .set(KnowledgeDocument::getDocType, parsed.docType()));
    }

    private List<TextSegment> split(ParsedDocument parsed,
                                    String docKey,
                                    String source,
                                    String department,
                                    String docHash) {
        Metadata metadata = new Metadata()
                .put("doc_key", docKey)
                .put("doc_hash", docHash)
                .put("source", source)
                .put("title", parsed.title())
                .put("doc_type", parsed.docType())
                .put("department", department == null ? "" : department)
                .put("structure_quality", parsed.structureQuality())
                .put("parser", parsed.parserName());

        Document document = Document.from(parsed.markdown(), metadata);
        List<TextSegment> segments = documentSplitter.split(document);
        List<TextSegment> chunks = new ArrayList<>();

        int chunkIndex = 0;
        for (TextSegment segment : segments) {
            String content = segment.text().strip();
            if (content.isBlank()) {
                continue;
            }

            String contentHash = HashUtils.sha256(content);
            String chunkId = HashUtils.sha256(docKey + ":" + docHash + ":" + chunkIndex + ":" + contentHash)
                    .substring(0, MILVUS_ID_MAX_LENGTH);
            Metadata chunkMetadata = segment.metadata().copy()
                    .put("chunk_id", chunkId)
                    .put("chunk_index", chunkIndex)
                    .put("content_hash", contentHash);

            chunks.add(TextSegment.from(content, chunkMetadata));
            chunkIndex++;
        }

        return chunks;
    }

    private List<Embedding> embed(List<TextSegment> chunks) {
        Response<List<Embedding>> response = embeddingModel.embedAll(chunks);
        List<Embedding> embeddings = response.content();
        if (embeddings == null || embeddings.size() != chunks.size()) {
            throw new IllegalStateException("Embedding 数量与切片数量不一致");
        }

        int expectedDimension = ragProperties.getEmbeddingDimension();
        for (Embedding embedding : embeddings) {
            if (embedding.dimension() != expectedDimension) {
                throw new IllegalStateException("Embedding 维度不一致: expected="
                        + expectedDimension + ", actual=" + embedding.dimension());
            }
        }
        return embeddings;
    }

    private void removeOldChunks(KnowledgeDocument document) {
        List<KnowledgeChunk> oldChunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunk>()
                        .eq(KnowledgeChunk::getDocumentId, document.getId()));

        if (oldChunks.isEmpty()) {
            return;
        }

        List<String> oldChunkIds = oldChunks.stream()
                .map(KnowledgeChunk::getChunkId)
                .toList();
        embeddingStore.removeAll(oldChunkIds);
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, document.getId()));
    }

    private void insertChunkRows(KnowledgeDocument document, String docKey, List<TextSegment> chunks) {
        for (TextSegment chunk : chunks) {
            KnowledgeChunk row = new KnowledgeChunk();
            row.setDocumentId(document.getId());
            row.setChunkId(chunk.metadata().getString("chunk_id"));
            row.setDocKey(docKey);
            row.setChunkIndex(chunk.metadata().getInteger("chunk_index"));
            row.setContentHash(chunk.metadata().getString("content_hash"));
            row.setContent(chunk.text());
            chunkMapper.insert(row);
        }
    }

    private void cleanupNewData(List<String> chunkIds) {
        if (chunkIds.isEmpty()) {
            return;
        }
        try {
            chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                    .in(KnowledgeChunk::getChunkId, chunkIds));
            embeddingStore.removeAll(chunkIds);
        } catch (Exception cleanupError) {
            log.warn("清理失败数据时出错，chunkIds={}", chunkIds, cleanupError);
        }
    }

    private void markReady(KnowledgeDocument document, int chunkCount) {
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, document.getId())
                .set(KnowledgeDocument::getStatus, STATUS_READY)
                .set(KnowledgeDocument::getChunkCount, chunkCount)
                .set(KnowledgeDocument::getErrorMessage, null));
        document.setStatus(STATUS_READY);
        document.setChunkCount(chunkCount);
        document.setErrorMessage(null);
    }

    private void markFailed(KnowledgeDocument document, String message) {
        String errorMessage = message == null ? "未知错误" : message;
        if (errorMessage.length() > 1000) {
            errorMessage = errorMessage.substring(0, 1000);
        }
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, document.getId())
                .set(KnowledgeDocument::getStatus, STATUS_FAILED)
                .set(KnowledgeDocument::getErrorMessage, errorMessage));
        document.setStatus(STATUS_FAILED);
        document.setErrorMessage(errorMessage);
    }

    private KnowledgeDocument findByDocKey(String docKey) {
        return documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getDocKey, docKey));
    }

    private KnowledgeIngestResult toResult(KnowledgeDocument document, boolean skipped) {
        int chunkCount = document.getChunkCount() == null ? 0 : document.getChunkCount();
        return new KnowledgeIngestResult(document.getId(), document.getDocKey(),
                document.getTitle(), chunkCount, skipped, document.getStatus());
    }

    private String hashFile(Path file) {
        try {
            return HashUtils.sha256(Files.readAllBytes(file));
        } catch (IOException e) {
            throw ApiException.badRequest("读取文件失败: " + e.getMessage());
        }
    }

    private String normalizeSource(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "unknown";
        }
        String name = originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.trim();
    }

    private String docTypeOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "UNKNOWN";
        }
        return name.substring(dot + 1).toUpperCase(Locale.ROOT);
    }
}
