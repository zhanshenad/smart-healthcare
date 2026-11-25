package com.atguigu.java.ai.langchain4j.service.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ModelKeyCrypto {

    private static final Logger log = LoggerFactory.getLogger(ModelKeyCrypto.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_LENGTH = 32;

    private final SecureRandom random = new SecureRandom();
    private final SecretKey key;

    public ModelKeyCrypto(@Value("${app.model-key-secret:}") String secret) {
        this.key = decode(secret);
        if (key == null) {
            log.warn("未配置 app.model-key-secret，用户 API Key 保存功能不可用");
        }
    }

    public boolean isAvailable() {
        return key != null;
    }

    public String encrypt(long userId, String provider, String rawKey) {
        requireAvailable();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(userId, provider));
            byte[] cipherText = cipher.doFinal(rawKey.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    public String decrypt(long userId, String provider, String encrypted) {
        requireAvailable();
        try {
            byte[] payload = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(userId, provider));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 解密失败", e);
        }
    }

    public String mask(String rawKey) {
        String value = rawKey == null ? "" : rawKey.trim();
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private void requireAvailable() {
        if (key == null) {
            throw new IllegalStateException("未配置 app.model-key-secret，无法处理用户 API Key");
        }
    }

    private byte[] aad(long userId, String provider) {
        return (userId + ":" + provider).getBytes(StandardCharsets.UTF_8);
    }

    private SecretKey decode(String secret) {
        if (!StringUtils.hasText(secret)) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(secret.trim());
            if (bytes.length != KEY_LENGTH) {
                log.error("app.model-key-secret 必须是 Base64 编码的 32 字节，当前长度={}", bytes.length);
                return null;
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException e) {
            log.error("app.model-key-secret 不是合法的 Base64 字符串");
            return null;
        }
    }
}
