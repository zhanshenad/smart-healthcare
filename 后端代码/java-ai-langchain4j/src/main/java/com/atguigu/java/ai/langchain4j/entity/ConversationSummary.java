package com.atguigu.java.ai.langchain4j.entity;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document("conversation_summaries")
public class ConversationSummary {

    // 一个会话只保留一条摘要，直接用 memoryId 作为主键
    @Id
    private String memoryId;

    private String summary;

    private int coveredMessageCount;

    private Instant updateTime;
}
