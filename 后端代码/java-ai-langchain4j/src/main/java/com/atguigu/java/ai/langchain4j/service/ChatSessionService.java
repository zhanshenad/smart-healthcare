package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.entity.ChatSession;
import com.atguigu.java.ai.langchain4j.mapper.ChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;

    public ChatSessionService(ChatSessionMapper chatSessionMapper) {
        this.chatSessionMapper = chatSessionMapper;
    }

    public void ensureOwner(Long userId, String memoryId) {
        ChatSession session = findByMemoryId(memoryId);
        if (session == null) {
            ChatSession newSession = new ChatSession();
            newSession.setUserId(userId);
            newSession.setMemoryId(memoryId);
            newSession.setCreatedAt(LocalDateTime.now());
            newSession.setUpdatedAt(LocalDateTime.now());
            try {
                chatSessionMapper.insert(newSession);
                return;
            } catch (DuplicateKeyException e) {
                session = findByMemoryId(memoryId);
            }
        }
        if (session == null || !session.getUserId().equals(userId)) {
            throw ApiException.forbidden("无权访问该会话");
        }
    }

    public Long requireUserId(String memoryId) {
        ChatSession session = findByMemoryId(memoryId);
        if (session == null) {
            throw ApiException.forbidden("会话不存在或无权访问");
        }
        return session.getUserId();
    }

    private ChatSession findByMemoryId(String memoryId) {
        return chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getMemoryId, memoryId));
    }
}
