package com.atguigu.java.ai.langchain4j.common;

import java.util.regex.Pattern;

// 日志和审计输出前做敏感信息脱敏
public final class SensitiveMasker {

    private static final Pattern ID_CARD_18 =
            Pattern.compile("(?<!\\d)(\\d{6})\\d{8}(\\d{3}[0-9Xx])(?!\\d)");
    private static final Pattern ID_CARD_15 =
            Pattern.compile("(?<!\\d)(\\d{6})\\d{6}(\\d{3})(?!\\d)");
    private static final Pattern API_KEY =
            Pattern.compile("(sk-[A-Za-z0-9._-]{3})[A-Za-z0-9._-]+");

    private SensitiveMasker() {
    }

    public static String maskIdCard(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = ID_CARD_18.matcher(text).replaceAll("$1********$2");
        return ID_CARD_15.matcher(masked).replaceAll("$1******$2");
    }

    public static String maskApiKey(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return API_KEY.matcher(text).replaceAll("$1****");
    }

    public static String mask(String text) {
        return maskApiKey(maskIdCard(text));
    }
}
