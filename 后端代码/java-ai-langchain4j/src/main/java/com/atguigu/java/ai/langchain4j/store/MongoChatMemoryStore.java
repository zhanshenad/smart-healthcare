package com.atguigu.java.ai.langchain4j.store;

import com.atguigu.java.ai.langchain4j.entity.ChatMessages;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MongoChatMemoryStore implements ChatMemoryStore {

    private final MongoTemplate mongoTemplate;

    public MongoChatMemoryStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        ChatMessages chatMessages = mongoTemplate.findOne(query(memoryId), ChatMessages.class);
        if (chatMessages == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(ChatMessageDeserializer.messagesFromJson(chatMessages.getContent()));
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        String id = normalize(memoryId);
        Update update = new Update()
                .set("memoryId", id)
                .set("content", ChatMessageSerializer.messagesToJson(list));

        try {
            mongoTemplate.upsert(query(id), update, ChatMessages.class);
        } catch (DuplicateKeyException e) {
            // 首次并发写入时可能同时触发 upsert，唯一索引会让其中一个失败，这里重试一次
            log.debug("聊天记录并发写入冲突，重试 memoryId={}", id);
            mongoTemplate.upsert(query(id), update, ChatMessages.class);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        mongoTemplate.remove(query(memoryId), ChatMessages.class);
    }

    private Query query(Object memoryId) {
        return query(normalize(memoryId));
    }

    private Query query(String memoryId) {
        return Query.query(Criteria.where("memoryId").is(memoryId));
    }

    private String normalize(Object memoryId) {
        return String.valueOf(memoryId);
    }
}
