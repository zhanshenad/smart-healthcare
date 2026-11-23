package com.atguigu.java.ai.langchain4j.store;

import com.atguigu.java.ai.langchain4j.entity.ChatMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatMemoryIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    public ChatMemoryIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            mongoTemplate.indexOps(ChatMessages.class)
                    .ensureIndex(new Index().on("memoryId", Sort.Direction.ASC).unique());
        } catch (Exception e) {
            // 旧数据可能已有重复 memoryId，先记录日志，不阻塞启动
            log.error("聊天记录唯一索引创建失败，请清理 chat_messages 中重复的 memoryId 后重启: {}", e.getMessage());
        }
    }
}
