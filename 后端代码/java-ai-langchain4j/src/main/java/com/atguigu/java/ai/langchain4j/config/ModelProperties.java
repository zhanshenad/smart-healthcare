package com.atguigu.java.ai.langchain4j.config;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "xiaozhi.models")
public class ModelProperties {

    private String defaultModelId = "qwen4-max";

    private List<Entry> entries = new ArrayList<>();

    private Map<String, String> defaultApiKeys = new HashMap<>();

    @Data
    public static class Entry {

        private String id;

        private String provider;

        private String baseUrl;

        private String modelName;

        private String protocol = "openai-compatible";

        private boolean streaming = true;

        private boolean tools = true;
    }

    // 请求未指定模型时返回默认模型
    public String resolveModelId(String modelId) {
        return StringUtils.hasText(modelId) ? modelId.trim() : defaultModelId;
    }

    public Entry require(String modelId) {
        String target = resolveModelId(modelId);
        return entries.stream()
                .filter(entry -> target.equals(entry.getId()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("不支持的模型: " + target));
    }

    public List<Entry> entriesOf(String provider) {
        return entries.stream()
                .filter(entry -> provider != null && provider.equals(entry.getProvider()))
                .toList();
    }

    public boolean hasProvider(String provider) {
        return !entriesOf(provider).isEmpty();
    }

    public String defaultApiKey(String provider) {
        return defaultApiKeys.get(provider);
    }
}
