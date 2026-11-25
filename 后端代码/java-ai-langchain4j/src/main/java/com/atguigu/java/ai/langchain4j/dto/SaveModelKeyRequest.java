package com.atguigu.java.ai.langchain4j.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveModelKeyRequest {

    @NotBlank(message = "provider不能为空")
    private String provider;

    @NotBlank(message = "apiKey不能为空")
    private String apiKey;
}
