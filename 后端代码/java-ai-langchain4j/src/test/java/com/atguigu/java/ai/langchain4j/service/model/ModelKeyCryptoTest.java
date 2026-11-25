package com.atguigu.java.ai.langchain4j.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ModelKeyCryptoTest {

    private final ModelKeyCrypto crypto = new ModelKeyCrypto(randomSecret());

    @Test
    void shouldEncryptAndDecrypt() {
        String rawKey = "sk-ws-test-1234567890";

        String encrypted = crypto.encrypt(1L, "dashscope", rawKey);

        assertThat(encrypted).isNotEqualTo(rawKey);
        assertThat(crypto.decrypt(1L, "dashscope", encrypted)).isEqualTo(rawKey);
    }

    @Test
    void shouldRejectDifferentUser() {
        String encrypted = crypto.encrypt(1L, "dashscope", "sk-ws-test-1234567890");

        assertThatThrownBy(() -> crypto.decrypt(2L, "dashscope", encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectDifferentProvider() {
        String encrypted = crypto.encrypt(1L, "dashscope", "sk-ws-test-1234567890");

        assertThatThrownBy(() -> crypto.decrypt(1L, "deepseek", encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldMaskKey() {
        assertThat(crypto.mask("sk-ws-test-1234567890")).isEqualTo("sk-****7890");
        assertThat(crypto.mask("short")).isEqualTo("****");
    }

    @Test
    void shouldDisableWhenSecretMissing() {
        ModelKeyCrypto unavailable = new ModelKeyCrypto("");

        assertThat(unavailable.isAvailable()).isFalse();
        assertThatThrownBy(() -> unavailable.encrypt(1L, "dashscope", "sk-test"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
