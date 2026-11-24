package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.entity.KnowledgeChunk;
import com.atguigu.java.ai.langchain4j.mapper.KnowledgeChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeChunkWriter {

    private final KnowledgeChunkMapper chunkMapper;

    public KnowledgeChunkWriter(KnowledgeChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    @Transactional
    public void replace(Long documentId, String docKey, List<TextSegment> chunks) {
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId));

        for (TextSegment chunk : chunks) {
            KnowledgeChunk row = new KnowledgeChunk();
            row.setDocumentId(documentId);
            row.setChunkId(chunk.metadata().getString("chunk_id"));
            row.setDocKey(docKey);
            row.setChunkIndex(chunk.metadata().getInteger("chunk_index"));
            row.setContentHash(chunk.metadata().getString("content_hash"));
            row.setContent(chunk.text());
            row.setOutlinePath(chunk.metadata().getString("outline_path"));
            row.setParentId(chunk.metadata().getString("parent_id"));
            row.setRole(chunk.metadata().getString("role"));
            row.setStrategy(chunk.metadata().getString("strategy"));
            chunkMapper.insert(row);
        }
    }
}
