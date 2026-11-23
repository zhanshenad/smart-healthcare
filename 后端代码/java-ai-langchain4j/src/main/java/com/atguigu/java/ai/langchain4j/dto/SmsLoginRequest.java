package com.atguigu.java.ai.langchain4j.dto;

import com.atguigu.java.ai.langchain4j.common.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SmsLoginRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = ValidationPatterns.PHONE, message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "\\d{6}", message = "验证码必须是6位数字")
        String code) {
}
