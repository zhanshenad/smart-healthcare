package com.atguigu.java.ai.langchain4j.service.model;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.config.ModelProperties;
import com.atguigu.java.ai.langchain4j.entity.UserModelKey;
import com.atguigu.java.ai.langchain4j.mapper.UserModelKeyMapper;
import com.atguigu.java.ai.langchain4j.vo.ModelOption;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserModelKeyService {

    private static final Logger log = LoggerFactory.getLogger(UserModelKeyService.class);

    private static final int STATUS_ENABLED = 1;

    private final UserModelKeyMapper keyMapper;
    private final ModelProperties modelProperties;
    private final ModelKeyCrypto crypto;
    private final CapabilityTester capabilityTester;
    private final ApplicationEventPublisher eventPublisher;

    public UserModelKeyService(UserModelKeyMapper keyMapper,
                               ModelProperties modelProperties,
                               ModelKeyCrypto crypto,
                               CapabilityTester capabilityTester,
                               ApplicationEventPublisher eventPublisher) {
        this.keyMapper = keyMapper;
        this.modelProperties = modelProperties;
        this.crypto = crypto;
        this.capabilityTester = capabilityTester;
        this.eventPublisher = eventPublisher;
    }

    public List<ModelOption> list(Long userId) {
        Map<String, UserModelKey> configured = keyMapper.selectList(
                        new LambdaQueryWrapper<UserModelKey>()
                                .eq(UserModelKey::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(
                        UserModelKey::getProvider,
                        Function.identity(),
                        (first, second) -> first));

        return modelProperties.getEntries().stream()
                .map(entry -> {
                    UserModelKey row = configured.get(entry.getProvider());
                    boolean enabled = isEnabled(row);
                    boolean fallback = !enabled
                            && StringUtils.hasText(modelProperties.defaultApiKey(entry.getProvider()));
                    return new ModelOption(
                            entry.getId(),
                            entry.getProvider(),
                            entry.getModelName(),
                            enabled,
                            enabled ? row.getKeyMasked() : null,
                            fallback);
                })
                .toList();
    }

    public void save(Long userId, String provider, String rawKey) {
        String targetProvider = normalizeProvider(provider);
        String value = rawKey == null ? "" : rawKey.trim();
        if (!StringUtils.hasText(value)) {
            throw ApiException.badRequest("API Key 不能为空");
        }
        if (!crypto.isAvailable()) {
            throw ApiException.badRequest("服务端未配置 API Key 加密主密钥，暂时无法保存");
        }

        capabilityTester.verify(targetProvider, value);

        String encrypted = crypto.encrypt(userId, targetProvider, value);
        String masked = crypto.mask(value);

        UserModelKey existing = find(userId, targetProvider);
        if (existing == null) {
            UserModelKey row = new UserModelKey();
            row.setUserId(userId);
            row.setProvider(targetProvider);
            row.setEncryptedKey(encrypted);
            row.setKeyVersion(1);
            row.setKeyMasked(masked);
            row.setStatus(STATUS_ENABLED);
            try {
                keyMapper.insert(row);
                log.info("用户模型 Key 已保存，userId={}, provider={}", userId, targetProvider);
                eventPublisher.publishEvent(new ModelKeyChangedEvent(userId));
                return;
            } catch (DuplicateKeyException e) {
                existing = find(userId, targetProvider);
                if (existing == null) {
                    throw e;
                }
            }
        }

        existing.setEncryptedKey(encrypted);
        existing.setKeyMasked(masked);
        existing.setKeyVersion((existing.getKeyVersion() == null ? 0 : existing.getKeyVersion()) + 1);
        existing.setStatus(STATUS_ENABLED);
        keyMapper.updateById(existing);
        log.info("用户模型 Key 已更新，userId={}, provider={}", userId, targetProvider);
        eventPublisher.publishEvent(new ModelKeyChangedEvent(userId));
    }

    public void delete(Long userId, String provider) {
        String targetProvider = normalizeProvider(provider);
        keyMapper.delete(new LambdaQueryWrapper<UserModelKey>()
                .eq(UserModelKey::getUserId, userId)
                .eq(UserModelKey::getProvider, targetProvider));
        log.info("用户模型 Key 已删除，userId={}, provider={}", userId, targetProvider);
        eventPublisher.publishEvent(new ModelKeyChangedEvent(userId));
    }

    public ResolvedKey resolve(Long userId, String provider) {
        UserModelKey row = find(userId, provider);
        if (isEnabled(row)) {
            try {
                String apiKey = crypto.decrypt(userId, provider, row.getEncryptedKey());
                int version = row.getKeyVersion() == null ? 0 : row.getKeyVersion();
                return new ResolvedKey(apiKey, version, false);
            } catch (IllegalStateException e) {
                throw ApiException.badRequest("API Key 解密失败，请重新配置");
            }
        }

        String fallbackKey = modelProperties.defaultApiKey(provider);
        if (StringUtils.hasText(fallbackKey)) {
            return new ResolvedKey(fallbackKey, 0, true);
        }
        throw ApiException.badRequest("请先配置 " + provider + " 的 API Key");
    }

    private String normalizeProvider(String provider) {
        String value = provider == null ? "" : provider.trim();
        if (!modelProperties.hasProvider(value)) {
            throw ApiException.badRequest("不支持的 provider: " + value);
        }
        return value;
    }

    private UserModelKey find(Long userId, String provider) {
        return keyMapper.selectOne(new LambdaQueryWrapper<UserModelKey>()
                .eq(UserModelKey::getUserId, userId)
                .eq(UserModelKey::getProvider, provider));
    }

    private boolean isEnabled(UserModelKey row) {
        return row != null && Integer.valueOf(STATUS_ENABLED).equals(row.getStatus());
    }
}
