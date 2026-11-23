package com.atguigu.java.ai.langchain4j.security;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static LoginUser get() {
        LoginUser user = getOrNull();
        if (user == null) {
            throw ApiException.unauthorized("请先登录");
        }
        return user;
    }

    public static LoginUser getOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof LoginUser user
                ? user
                : null;
    }

    public static Long id() {
        return get().userId();
    }
}
