package com.atguigu.java.ai.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.atguigu.java.ai.langchain4j.entity.ChatMessages;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@SpringBootTest
class MongoCrudTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void shouldInsertAndFindMessage() {
        ChatMessages inserted = insertMessage("聊天记录列表");

        ChatMessages found = mongoTemplate.findById(inserted.getMessageId(), ChatMessages.class);

        assertThat(found).isNotNull();
        assertThat(found.getContent()).isEqualTo("聊天记录列表");
        cleanup(inserted.getMessageId());
    }

    @Test
    void shouldUpdateMessageContent() {
        ChatMessages inserted = insertMessage("旧内容");

        Query query = new Query(Criteria.where("_id").is(inserted.getMessageId()));
        mongoTemplate.updateFirst(query, new Update().set("content", "新的聊天记录列表"), ChatMessages.class);

        ChatMessages updated = mongoTemplate.findById(inserted.getMessageId(), ChatMessages.class);
        assertThat(updated.getContent()).isEqualTo("新的聊天记录列表");
        cleanup(inserted.getMessageId());
    }

    @Test
    void shouldDeleteMessage() {
        ChatMessages inserted = insertMessage("待删除记录");

        mongoTemplate.remove(new Query(Criteria.where("_id").is(inserted.getMessageId())), ChatMessages.class);

        assertThat(mongoTemplate.findById(inserted.getMessageId(), ChatMessages.class)).isNull();
    }

    private ChatMessages insertMessage(String content) {
        ChatMessages message = new ChatMessages();
        message.setMemoryId(UUID.randomUUID().toString());
        message.setContent(content);
        return mongoTemplate.insert(message);
    }

    private void cleanup(ObjectId id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), ChatMessages.class);
    }
}
