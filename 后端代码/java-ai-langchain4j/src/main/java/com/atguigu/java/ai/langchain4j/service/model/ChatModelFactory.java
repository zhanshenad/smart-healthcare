package com.atguigu.java.ai.langchain4j.service.model;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.config.ModelProperties;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChatModelFactory {

    private static final String OPENAI_COMPATIBLE = "openai-compatible";

    public ChatLanguageModel chatModel(ModelProperties.Entry entry, String apiKey) {
        return chatModel(entry, apiKey, null);
    }

    public ChatLanguageModel chatModel(ModelProperties.Entry entry, String apiKey, Integer maxTokens) {
        requireCompatible(entry);
        ModelSettings settings = settings(entry, apiKey, maxTokens);
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(settings.baseUrl())
                .apiKey(settings.apiKey())
                .modelName(settings.modelName());
        if (settings.maxTokens() != null) {
            builder.maxTokens(settings.maxTokens());
        }
        return builder.build();
    }

    public StreamingChatLanguageModel streamingModel(ModelProperties.Entry entry, String apiKey) {
        return streamingModel(entry, apiKey, null);
    }

    public StreamingChatLanguageModel streamingModel(ModelProperties.Entry entry,
                                                     String apiKey,
                                                     Integer maxTokens) {
        requireCompatible(entry);
        ModelSettings settings = settings(entry, apiKey, maxTokens);
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .baseUrl(settings.baseUrl())
                .apiKey(settings.apiKey())
                .modelName(settings.modelName());
        if (settings.maxTokens() != null) {
            builder.maxTokens(settings.maxTokens());
        }
        return builder.build();
    }

    private ModelSettings settings(ModelProperties.Entry entry, String apiKey, Integer maxTokens) {
        return new ModelSettings(entry.getBaseUrl(), apiKey, entry.getModelName(), maxTokens);
    }

    // 普通和流式模型共用同一组构造参数
    private record ModelSettings(String baseUrl, String apiKey, String modelName, Integer maxTokens) {
    }

    private void requireCompatible(ModelProperties.Entry entry) {
        if (StringUtils.hasText(entry.getProtocol())
                && !OPENAI_COMPATIBLE.equals(entry.getProtocol())) {
            throw ApiException.badRequest("暂不支持的模型协议: " + entry.getProtocol());
        }
    }
}
