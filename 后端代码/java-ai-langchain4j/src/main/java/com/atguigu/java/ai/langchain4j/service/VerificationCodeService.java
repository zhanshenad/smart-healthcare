package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.config.SmsProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VerificationCodeService {

    private final SmsProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();

    public VerificationCodeService(SmsProperties properties) {
        this.properties = properties;
    }

    public void send(String phone) {
        cleanExpired();
        CodeEntry entry = codes.computeIfAbsent(phone, key -> new CodeEntry());
        synchronized (entry) {
            Instant now = Instant.now();
            if (entry.lastSentAt != null
                    && now.isBefore(entry.lastSentAt.plusSeconds(properties.getSendIntervalSeconds()))) {
                throw ApiException.badRequest("验证码发送过于频繁，请稍后再试");
            }
            if (!LocalDate.now().equals(entry.sendDate)) {
                entry.sendDate = LocalDate.now();
                entry.sendCount = 0;
            }
            if (entry.sendCount >= properties.getMaxDailySends()) {
                throw ApiException.badRequest("今日验证码发送次数已达上限");
            }

            String code = String.format("%06d", random.nextInt(1_000_000));
            entry.code = code;
            entry.expiresAt = now.plusSeconds(properties.getCodeTtlMinutes() * 60L);
            entry.lastSentAt = now;
            entry.attempts = 0;
            entry.sendCount++;

            if (properties.isDebug()) {
                log.info("短信验证码 phone={} code={} expiresIn={}分钟", phone, code, properties.getCodeTtlMinutes());
            } else {
                log.info("短信验证码已发送 phone={}", phone);
            }
        }
    }

    public void verify(String phone, String code) {
        CodeEntry entry = codes.get(phone);
        if (entry == null) {
            throw ApiException.badRequest("验证码无效或已过期");
        }
        synchronized (entry) {
            if (entry.expiresAt == null || Instant.now().isAfter(entry.expiresAt)) {
                codes.remove(phone, entry);
                throw ApiException.badRequest("验证码已过期");
            }
            if (entry.attempts >= properties.getMaxVerifyAttempts()) {
                codes.remove(phone, entry);
                throw ApiException.badRequest("验证码错误次数过多，请重新获取");
            }
            if (!MessageDigest.isEqual(
                    entry.code.getBytes(StandardCharsets.UTF_8),
                    code.getBytes(StandardCharsets.UTF_8))) {
                entry.attempts++;
                if (entry.attempts >= properties.getMaxVerifyAttempts()) {
                    codes.remove(phone, entry);
                    throw ApiException.badRequest("验证码错误次数过多，请重新获取");
                }
                throw ApiException.badRequest("验证码错误");
            }
            // 验证码一次性使用
            codes.remove(phone, entry);
        }
    }

    private void cleanExpired() {
        Instant now = Instant.now();
        codes.entrySet().removeIf(item -> {
            CodeEntry entry = item.getValue();
            synchronized (entry) {
                return entry.expiresAt != null && now.isAfter(entry.expiresAt);
            }
        });
    }

    private static class CodeEntry {
        private String code;
        private Instant expiresAt;
        private Instant lastSentAt;
        private int attempts;
        private int sendCount;
        private LocalDate sendDate;
    }
}
