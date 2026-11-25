package com.atguigu.java.ai.langchain4j.service.model;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.config.ModelProperties;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class CapabilityTester {

    private static final String PROMPT = "请只回复 ok";
    private static final int TEST_MAX_TOKENS = 64;
    private static final long TIMEOUT_SECONDS = 15;

    private final ModelProperties modelProperties;
    private final ChatModelFactory chatModelFactory;

    public CapabilityTester(ModelProperties modelProperties, ChatModelFactory chatModelFactory) {
        this.modelProperties = modelProperties;
        this.chatModelFactory = chatModelFactory;
    }

    public void verify(String provider, String apiKey) {
        ModelProperties.Entry entry = modelProperties.entriesOf(provider).stream()
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("不支持的 provider: " + provider));

        verifyChat(entry, apiKey);
        if (entry.isStreaming()) {
            verifyStreaming(entry, apiKey);
        }
    }

    private void verifyChat(ModelProperties.Entry entry, String apiKey) {
        try {
            String content = chatModelFactory
                    .chatModel(entry, apiKey, TEST_MAX_TOKENS)
                    .chat(PROMPT);
            if (content == null) {
                throw new IllegalStateException("模型没有返回响应");
            }
        } catch (Exception e) {
            throw ApiException.badRequest("API Key 校验失败: " + describe(e, apiKey));
        }
    }

    private void verifyStreaming(ModelProperties.Entry entry, String apiKey) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            chatModelFactory.streamingModel(entry, apiKey, TEST_MAX_TOKENS)
                    .chat(PROMPT, new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialResponse) {
                            latch.countDown();
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse completeResponse) {
                            latch.countDown();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            error.set(throwable);
                            latch.countDown();
                        }
                    });
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("流式响应超时");
            }
            if (error.get() != null) {
                throw new IllegalStateException(error.get());
            }
        } catch (Exception e) {
            throw ApiException.badRequest("API Key 流式校验失败: " + describe(e, apiKey));
        }
    }

    private String describe(Exception e, String apiKey) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (apiKey != null && !apiKey.isBlank()) {
            message = message.replace(apiKey, "***");
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
