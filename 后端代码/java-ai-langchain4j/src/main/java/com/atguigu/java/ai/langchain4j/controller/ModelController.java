package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.common.ApiResponse;
import com.atguigu.java.ai.langchain4j.config.ModelProperties;
import com.atguigu.java.ai.langchain4j.dto.SaveModelKeyRequest;
import com.atguigu.java.ai.langchain4j.security.CurrentUser;
import com.atguigu.java.ai.langchain4j.service.model.UserModelKeyService;
import com.atguigu.java.ai.langchain4j.vo.ModelOptionsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "模型配置")
@RestController
@RequestMapping("/medical")
public class ModelController {

    private final UserModelKeyService keyService;
    private final ModelProperties modelProperties;

    public ModelController(UserModelKeyService keyService, ModelProperties modelProperties) {
        this.keyService = keyService;
        this.modelProperties = modelProperties;
    }

    @Operation(summary = "查看可用模型和 Key 配置状态")
    @GetMapping("/models")
    public ApiResponse<ModelOptionsResponse> models() {
        Long userId = CurrentUser.id();
        return ApiResponse.ok(new ModelOptionsResponse(
                modelProperties.getDefaultModelId(),
                keyService.list(userId)));
    }

    @Operation(summary = "保存用户模型 API Key")
    @PutMapping("/model-keys")
    public ApiResponse<Void> saveKey(@Valid @RequestBody SaveModelKeyRequest request) {
        keyService.save(CurrentUser.id(), request.getProvider(), request.getApiKey());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除用户模型 API Key")
    @DeleteMapping("/model-keys/{provider}")
    public ApiResponse<Void> deleteKey(@PathVariable("provider") String provider) {
        keyService.delete(CurrentUser.id(), provider);
        return ApiResponse.ok();
    }
}
