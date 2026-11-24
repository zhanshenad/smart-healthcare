package com.atguigu.java.ai.langchain4j.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("knowledge_chunks")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private String chunkId;

    private String docKey;

    private Integer chunkIndex;

    private String contentHash;

    private String content;

    private LocalDateTime createdAt;
}
