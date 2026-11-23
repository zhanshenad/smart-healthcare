package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.common.ApiResponse;
import com.atguigu.java.ai.langchain4j.dto.PasswordLoginRequest;
import com.atguigu.java.ai.langchain4j.dto.RefreshTokenRequest;
import com.atguigu.java.ai.langchain4j.dto.SendCodeRequest;
import com.atguigu.java.ai.langchain4j.dto.SetPasswordRequest;
import com.atguigu.java.ai.langchain4j.dto.SmsLoginRequest;
import com.atguigu.java.ai.langchain4j.security.CurrentUser;
import com.atguigu.java.ai.langchain4j.service.AuthService;
import com.atguigu.java.ai.langchain4j.vo.AuthResponse;
import com.atguigu.java.ai.langchain4j.vo.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "发送短信验证码")
    @PostMapping("/sms/send")
    public ApiResponse<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendCode(request.phone());
        return ApiResponse.ok();
    }

    @Operation(summary = "验证码登录，首次登录自动注册")
    @PostMapping("/login/sms")
    public ApiResponse<AuthResponse> loginBySms(@Valid @RequestBody SmsLoginRequest request) {
        return ApiResponse.ok(authService.loginBySms(request.phone(), request.code()));
    }

    @Operation(summary = "密码登录")
    @PostMapping("/login/password")
    public ApiResponse<AuthResponse> loginByPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return ApiResponse.ok(authService.loginByPassword(request.phone(), request.password()));
    }

    @Operation(summary = "设置密码")
    @PostMapping("/password/set")
    public ApiResponse<Void> setPassword(@Valid @RequestBody SetPasswordRequest request) {
        authService.setPassword(CurrentUser.id(), request.password());
        return ApiResponse.ok();
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public ApiResponse<UserInfo> me() {
        return ApiResponse.ok(authService.me(CurrentUser.id()));
    }
}
