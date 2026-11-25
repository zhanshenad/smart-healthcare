package com.atguigu.java.ai.langchain4j.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atguigu.java.ai.langchain4j.assistant.XiaozhiAgent;
import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.config.ModelProperties;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XiaozhiAgentRegistryTest {

    @Mock
    private UserModelKeyService keyService;

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private XiaozhiAgentFactory agentFactory;

    @Mock
    private ChatLanguageModel chatModel;

    @Mock
    private StreamingChatLanguageModel streamingModel;

    @Mock
    private XiaozhiAgent agent;

    private ModelProperties modelProperties;

    private XiaozhiAgentRegistry registry;

    @BeforeEach
    void setUp() {
        modelProperties = new ModelProperties();
        modelProperties.setDefaultModelId("qwen4-max");
        modelProperties.setEntries(List.of(entry("qwen4-max", "dashscope", true)));
        registry = new XiaozhiAgentRegistry(
                modelProperties, keyService, chatModelFactory, agentFactory);

        lenient().when(keyService.resolve(any(), any()))
                .thenReturn(new ResolvedKey("sk-test", 1, false));
        lenient().when(chatModelFactory.chatModel(any(), any())).thenReturn(chatModel);
        lenient().when(chatModelFactory.streamingModel(any(), any())).thenReturn(streamingModel);
        lenient().when(agentFactory.create(any(), any(), any())).thenReturn(agent);
    }

    @Test
    void shouldCacheAgentForSameKey() {
        XiaozhiAgent first = registry.get(1L, "qwen4-max");
        XiaozhiAgent second = registry.get(1L, "qwen4-max");

        assertThat(first).isSameAs(second);
        verify(agentFactory, times(1)).create(any(), eq(chatModel), eq(streamingModel));
    }

    @Test
    void shouldRebuildAfterKeyChanged() {
        registry.get(1L, "qwen4-max");
        registry.onKeyChanged(new ModelKeyChangedEvent(1L));
        registry.get(1L, "qwen4-max");

        verify(agentFactory, times(2)).create(any(), eq(chatModel), eq(streamingModel));
    }

    @Test
    void shouldRejectUnknownModel() {
        assertThatThrownBy(() -> registry.get(1L, "unknown"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不支持的模型");
    }

    @Test
    void shouldRejectNonStreamingModel() {
        modelProperties.setEntries(List.of(entry("chat-only", "dashscope", false)));

        assertThatThrownBy(() -> registry.get(1L, "chat-only"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不支持流式对话");
    }

    private ModelProperties.Entry entry(String id, String provider, boolean streaming) {
        ModelProperties.Entry entry = new ModelProperties.Entry();
        entry.setId(id);
        entry.setProvider(provider);
        entry.setModelName(id);
        entry.setBaseUrl("https://example.com/v1");
        entry.setStreaming(streaming);
        return entry;
    }
}
