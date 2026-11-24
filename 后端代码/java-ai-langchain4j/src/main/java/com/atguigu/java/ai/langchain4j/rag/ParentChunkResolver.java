package com.atguigu.java.ai.langchain4j.rag;

import com.atguigu.java.ai.langchain4j.entity.KnowledgeChunk;
import com.atguigu.java.ai.langchain4j.mapper.KnowledgeChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ParentChunkResolver {

    private final KnowledgeChunkMapper chunkMapper;

    public ParentChunkResolver(KnowledgeChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    public Map<String, String> resolve(List<RetrievalHit> hits) {
        Set<String> parentIds = hits.stream()
                .map(RetrievalHit::getParentId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        if (parentIds.isEmpty()) {
            return Map.of();
        }

        List<KnowledgeChunk> parents = chunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunk>()
                        .in(KnowledgeChunk::getChunkId, parentIds));
        return parents.stream()
                .filter(parent -> StringUtils.hasText(parent.getContent()))
                .collect(Collectors.toMap(
                        KnowledgeChunk::getChunkId,
                        KnowledgeChunk::getContent,
                        (first, second) -> first));
    }
}
