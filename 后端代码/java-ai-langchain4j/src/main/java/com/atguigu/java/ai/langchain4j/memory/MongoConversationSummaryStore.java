package com.atguigu.java.ai.langchain4j.memory;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MongoConversationSummaryStore {

    private final MongoTemplate mongoTemplate;

    public MongoConversationSummaryStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String findSummary(String memoryId) {
        ConversationSummary summary = mongoTemplate.findById(memoryId, ConversationSummary.class);
        return summary == null ? null : summary.getSummary();
    }

    public void mergeSummary(String memoryId, String summary, int coveredMessageCount) {
        ConversationSummary existing = mongoTemplate.findById(memoryId, ConversationSummary.class);
        int totalCovered = coveredMessageCount;
        if (existing != null) {
            totalCovered += existing.getCoveredMessageCount();
        }
        mongoTemplate.save(new ConversationSummary(memoryId, summary, totalCovered, Instant.now()));
    }

    public void delete(String memoryId) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(memoryId)), ConversationSummary.class);
    }
}
