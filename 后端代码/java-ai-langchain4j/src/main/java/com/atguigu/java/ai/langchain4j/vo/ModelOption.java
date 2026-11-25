package com.atguigu.java.ai.langchain4j.vo;

public record ModelOption(String id,
                          String provider,
                          String modelName,
                          boolean configured,
                          String maskedKey,
                          boolean fallback) {
}
