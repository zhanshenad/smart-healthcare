package com.atguigu.java.ai.langchain4j.service.model;

import com.atguigu.java.ai.langchain4j.assistant.XiaozhiAgent;
import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.config.ModelProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class XiaozhiAgentRegistry {

    private static final int MAX_AGENTS = 200;
    private static final Duration EXPIRE_AFTER_ACCESS = Duration.ofMinutes(30);

    private final ModelProperties modelProperties;
    private final UserModelKeyService keyService;
    private final ChatModelFactory chatModelFactory;
    private final XiaozhiAgentFactory agentFactory;

    private final Cache<AgentKey, XiaozhiAgent> cache = Caffeine.newBuilder()
            .maximumSize(MAX_AGENTS)
            .expireAfterAccess(EXPIRE_AFTER_ACCESS)
            .build();

    public XiaozhiAgentRegistry(ModelProperties modelProperties,
                                UserModelKeyService keyService,
                                ChatModelFactory chatModelFactory,
                                XiaozhiAgentFactory agentFactory) {
        this.modelProperties = modelProperties;
        this.keyService = keyService;
        this.chatModelFactory = chatModelFactory;
        this.agentFactory = agentFactory;
    }

    public XiaozhiAgent get(Long userId, String modelId) {
        ModelProperties.Entry entry = modelProperties.require(modelId);
        if (!entry.isStreaming()) {
            throw ApiException.badRequest("该模型不支持流式对话: " + entry.getId());
        }

        ResolvedKey resolvedKey = keyService.resolve(userId, entry.getProvider());
        AgentKey cacheKey = new AgentKey(userId, entry.getId(), resolvedKey.fingerprint());

        return cache.get(cacheKey, key -> agentFactory.create(entry,
                chatModelFactory.chatModel(entry, resolvedKey.apiKey()),
                chatModelFactory.streamingModel(entry, resolvedKey.apiKey())));
    }

    @EventListener
    public void onKeyChanged(ModelKeyChangedEvent event) {
        cache.asMap().keySet().removeIf(key -> key.userId().equals(event.userId()));
    }

    record AgentKey(Long userId, String modelId, String keyFingerprint) {
    }
}
