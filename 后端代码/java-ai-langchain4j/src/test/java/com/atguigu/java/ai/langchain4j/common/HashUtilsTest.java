package com.atguigu.java.ai.langchain4j.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashUtilsTest {

    @Test
    void sha256ShouldBeStable() {
        assertThat(HashUtils.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void differentContentShouldProduceDifferentHash() {
        assertThat(HashUtils.sha256("a")).isNotEqualTo(HashUtils.sha256("b"));
    }
}
