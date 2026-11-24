package com.atguigu.java.ai.langchain4j.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("knowledge_documents")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String docKey;

    private String title;

    private String source;

    private String docType;

    private String department;

    private String docHash;

    private String status;

    private Integer chunkCount;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
