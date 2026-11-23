package com.atguigu.java.ai.langchain4j.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetPasswordRequest(
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度必须为8到64位")
        String password) {
}
