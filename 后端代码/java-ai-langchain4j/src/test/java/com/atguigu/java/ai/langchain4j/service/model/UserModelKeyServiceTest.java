package com.atguigu.java.ai.langchain4j.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.config.ModelProperties;
import com.atguigu.java.ai.langchain4j.entity.UserModelKey;
import com.atguigu.java.ai.langchain4j.mapper.UserModelKeyMapper;
import com.atguigu.java.ai.langchain4j.vo.ModelOption;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UserModelKeyServiceTest {

    @Mock
    private UserModelKeyMapper keyMapper;

    @Mock
    private ModelKeyCrypto crypto;

    @Mock
    private CapabilityTester capabilityTester;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ModelProperties modelProperties;

    private UserModelKeyService service;

    @BeforeEach
    void setUp() {
        modelProperties = new ModelProperties();
        modelProperties.setDefaultModelId("qwen4-max");
        ModelProperties.Entry entry = new ModelProperties.Entry();
        entry.setId("qwen4-max");
        entry.setProvider("dashscope");
        entry.setModelName("qwen4-max");
        modelProperties.setEntries(List.of(entry));
        service = new UserModelKeyService(
                keyMapper, modelProperties, crypto, capabilityTester, eventPublisher);
    }

    @Test
    void listShouldShowConfiguredKey() {
        UserModelKey row = key("dashscope", 1, "sk-****abcd");
        when(keyMapper.selectList(any())).thenReturn(List.of(row));

        List<ModelOption> options = service.list(1L);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).configured()).isTrue();
        assertThat(options.get(0).maskedKey()).isEqualTo("sk-****abcd");
        assertThat(options.get(0).fallback()).isFalse();
    }

    @Test
    void listShouldShowFallbackWhenNoUserKey() {
        when(keyMapper.selectList(any())).thenReturn(List.of());
        modelProperties.setDefaultApiKeys(Map.of("dashscope", "server-key"));

        List<ModelOption> options = service.list(1L);

        assertThat(options.get(0).configured()).isFalse();
        assertThat(options.get(0).fallback()).isTrue();
    }

    @Test
    void saveShouldVerifyAndInsert() {
        when(crypto.isAvailable()).thenReturn(true);
        when(crypto.encrypt(1L, "dashscope", "sk-user")).thenReturn("cipher");
        when(crypto.mask("sk-user")).thenReturn("sk-****user");
        when(keyMapper.selectOne(any())).thenReturn(null);

        service.save(1L, "dashscope", "sk-user");

        verify(capabilityTester).verify("dashscope", "sk-user");
        ArgumentCaptor<UserModelKey> captor = ArgumentCaptor.forClass(UserModelKey.class);
        verify(keyMapper).insert(captor.capture());
        UserModelKey saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getProvider()).isEqualTo("dashscope");
        assertThat(saved.getEncryptedKey()).isEqualTo("cipher");
        assertThat(saved.getKeyVersion()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(1);
        verify(eventPublisher).publishEvent(any(ModelKeyChangedEvent.class));
    }

    @Test
    void saveShouldIncrementVersionWhenUpdating() {
        UserModelKey existing = key("dashscope", 2, "sk-****old");
        when(crypto.isAvailable()).thenReturn(true);
        when(crypto.encrypt(1L, "dashscope", "sk-new")).thenReturn("cipher2");
        when(crypto.mask("sk-new")).thenReturn("sk-****new");
        when(keyMapper.selectOne(any())).thenReturn(existing);

        service.save(1L, "dashscope", "sk-new");

        ArgumentCaptor<UserModelKey> captor = ArgumentCaptor.forClass(UserModelKey.class);
        verify(keyMapper).updateById(captor.capture());
        assertThat(captor.getValue().getKeyVersion()).isEqualTo(3);
        assertThat(captor.getValue().getEncryptedKey()).isEqualTo("cipher2");
    }

    @Test
    void resolveShouldUseUserKey() {
        UserModelKey row = key("dashscope", 3, "sk-****abcd");
        row.setEncryptedKey("cipher");
        when(keyMapper.selectOne(any())).thenReturn(row);
        when(crypto.decrypt(1L, "dashscope", "cipher")).thenReturn("sk-user");

        ResolvedKey resolved = service.resolve(1L, "dashscope");

        assertThat(resolved.apiKey()).isEqualTo("sk-user");
        assertThat(resolved.keyVersion()).isEqualTo(3);
        assertThat(resolved.fallback()).isFalse();
        assertThat(resolved.fingerprint()).hasSize(8);
    }

    @Test
    void resolveShouldFallbackToServerKey() {
        when(keyMapper.selectOne(any())).thenReturn(null);
        modelProperties.setDefaultApiKeys(Map.of("dashscope", "server-key"));

        ResolvedKey resolved = service.resolve(1L, "dashscope");

        assertThat(resolved.apiKey()).isEqualTo("server-key");
        assertThat(resolved.keyVersion()).isZero();
        assertThat(resolved.fallback()).isTrue();
    }

    @Test
    void resolveShouldFailWithoutAnyKey() {
        when(keyMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.resolve(1L, "dashscope"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("请先配置");
    }

    @Test
    void deleteShouldRemoveRow() {
        service.delete(1L, "dashscope");

        verify(keyMapper).delete(any());
        verify(eventPublisher).publishEvent(any(ModelKeyChangedEvent.class));
    }

    private UserModelKey key(String provider, int version, String masked) {
        UserModelKey row = new UserModelKey();
        row.setUserId(1L);
        row.setProvider(provider);
        row.setKeyVersion(version);
        row.setKeyMasked(masked);
        row.setStatus(1);
        return row;
    }
}
