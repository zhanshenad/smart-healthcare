package com.atguigu.java.ai.langchain4j.common;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 工具调用审计，输出前脱敏
@Component
public class ToolAuditor {

    private static final Logger log = LoggerFactory.getLogger(ToolAuditor.class);

    public <T> T audit(String name, String memoryId, Object args, Supplier<T> action) {
        long start = System.currentTimeMillis();
        String maskedArgs = maskArgs(args);
        try {
            T result = action.get();
            log.info("tool.audit name={} memoryId={} costMs={} args={} result={}",
                    name, memoryId, System.currentTimeMillis() - start,
                    maskedArgs, truncate(SensitiveMasker.mask(String.valueOf(result))));
            return result;
        } catch (RuntimeException e) {
            log.warn("tool.audit name={} memoryId={} costMs={} args={} error={}",
                    name, memoryId, System.currentTimeMillis() - start,
                    maskedArgs, SensitiveMasker.mask(e.getMessage()));
            throw e;
        }
    }

    private String maskArgs(Object args) {
        return SensitiveMasker.mask(String.valueOf(args));
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
