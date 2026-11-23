package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.entity.User;
import com.atguigu.java.ai.langchain4j.security.JwtService;
import com.atguigu.java.ai.langchain4j.vo.AuthResponse;
import com.atguigu.java.ai.langchain4j.vo.UserInfo;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserService userService;
    private final VerificationCodeService verificationCodeService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserService userService,
                       VerificationCodeService verificationCodeService,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.verificationCodeService = verificationCodeService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public void sendCode(String phone) {
        verificationCodeService.send(phone);
    }

    public AuthResponse loginBySms(String phone, String code) {
        verificationCodeService.verify(phone, code);
        User user = userService.getOrCreateByPhone(phone);
        return issueTokens(user);
    }

    public AuthResponse loginByPassword(String phone, String password) {
        User user = userService.findByPhone(phone);
        if (user == null || user.getPasswordHash() == null) {
            throw ApiException.badRequest("该手机号未设置密码，请使用验证码登录");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("手机号或密码错误");
        }
        return issueTokens(user);
    }

    public AuthResponse refresh(String refreshToken) {
        Claims claims = jwtService.parseRefreshToken(refreshToken);
        if (claims == null) {
            throw ApiException.unauthorized("refreshToken无效或已过期");
        }
        Long userId = Long.valueOf(claims.getSubject());
        User user = userService.getById(userId);
        if (user == null) {
            throw ApiException.unauthorized("用户不存在");
        }
        return issueTokens(user);
    }

    public void setPassword(Long userId, String password) {
        User user = userService.getById(userId);
        if (user == null) {
            throw ApiException.unauthorized("用户不存在");
        }
        if (user.getPasswordHash() != null) {
            throw ApiException.badRequest("密码已设置，暂不支持重复设置");
        }
        User update = new User();
        update.setId(userId);
        update.setPasswordHash(passwordEncoder.encode(password));
        userService.updateById(update);
    }

    public UserInfo me(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw ApiException.unauthorized("用户不存在");
        }
        return toUserInfo(user);
    }

    private AuthResponse issueTokens(User user) {
        ensureActive(user);
        String accessToken = jwtService.createAccessToken(user.getId(), user.getPhone());
        String refreshToken = jwtService.createRefreshToken(user.getId(), user.getPhone());
        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getPasswordHash() == null,
                toUserInfo(user));
    }

    private void ensureActive(User user) {
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw ApiException.forbidden("账号已被禁用");
        }
    }

    private UserInfo toUserInfo(User user) {
        return new UserInfo(
                user.getId(),
                user.getPhone(),
                user.getNickname(),
                user.getPasswordHash() != null);
    }
}
