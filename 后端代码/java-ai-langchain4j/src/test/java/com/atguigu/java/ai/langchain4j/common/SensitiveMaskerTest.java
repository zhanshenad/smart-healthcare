package com.atguigu.java.ai.langchain4j.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveMaskerTest {

    @Test
    void shouldMask18DigitIdCard() {
        assertThat(SensitiveMasker.mask("身份证110101199001011234"))
                .isEqualTo("身份证110101********1234");
    }

    @Test
    void shouldMask15DigitIdCard() {
        assertThat(SensitiveMasker.mask("110101900101123"))
                .isEqualTo("110101******123");
    }

    @Test
    void shouldMaskApiKey() {
        assertThat(SensitiveMasker.mask("key=sk-ws-H.XDMMMEX.oUq9"))
                .isEqualTo("key=sk-ws-****");
    }

    @Test
    void shouldKeepNormalText() {
        assertThat(SensitiveMasker.mask("神经内科 2026-12-01 上午"))
                .isEqualTo("神经内科 2026-12-01 上午");
    }
}
