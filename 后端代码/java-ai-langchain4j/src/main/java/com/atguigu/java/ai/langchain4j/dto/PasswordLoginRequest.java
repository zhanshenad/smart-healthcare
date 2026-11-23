package com.atguigu.java.ai.langchain4j.dto;

import com.atguigu.java.ai.langchain4j.common.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordLoginRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = ValidationPatterns.PHONE, message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "密码不能为空")
        String password) {
}
