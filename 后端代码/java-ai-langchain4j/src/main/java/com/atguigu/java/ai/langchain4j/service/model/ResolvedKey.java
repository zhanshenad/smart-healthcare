package com.atguigu.java.ai.langchain4j.service.model;

import com.atguigu.java.ai.langchain4j.common.HashUtils;

public record ResolvedKey(String apiKey, int keyVersion, boolean fallback) {

    public String fingerprint() {
        return HashUtils.sha256(apiKey).substring(0, 8);
    }

    @Override
    public String toString() {
        return "ResolvedKey[keyVersion=" + keyVersion + ", fallback=" + fallback + "]";
    }
}
