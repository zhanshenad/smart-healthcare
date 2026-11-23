package com.atguigu.java.ai.langchain4j.vo;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        boolean needSetPassword,
        UserInfo user) {
}
