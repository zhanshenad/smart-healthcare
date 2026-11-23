package com.atguigu.java.ai.langchain4j.security;

import com.atguigu.java.ai.langchain4j.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService service() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("01234567890123456789012345678901");
        return new JwtService(properties);
    }

    @Test
    void accessAndRefreshTokenShouldBeSeparated() {
        JwtService jwtService = service();

        String accessToken = jwtService.createAccessToken(1L, "13800000001");
        String refreshToken = jwtService.createRefreshToken(1L, "13800000001");

        Claims accessClaims = jwtService.parseAccessToken(accessToken);
        Claims refreshClaims = jwtService.parseRefreshToken(refreshToken);

        assertThat(accessClaims).isNotNull();
        assertThat(accessClaims.getSubject()).isEqualTo("1");
        assertThat(jwtService.parseRefreshToken(accessToken)).isNull();

        assertThat(refreshClaims).isNotNull();
        assertThat(jwtService.parseAccessToken(refreshToken)).isNull();
    }

    @Test
    void invalidTokenShouldReturnNull() {
        assertThat(service().parse("not-a-token")).isNull();
    }
}
