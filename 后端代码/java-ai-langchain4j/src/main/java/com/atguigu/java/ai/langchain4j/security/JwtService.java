package com.atguigu.java.ai.langchain4j.security;

import com.atguigu.java.ai.langchain4j.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.getSecret() == null
                ? new byte[0]
                : properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 至少需要32字节");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String createAccessToken(Long userId, String phone) {
        Instant now = Instant.now();
        return createToken(userId, phone, TYPE_ACCESS, now,
                now.plus(properties.getAccessTokenTtlMinutes(), ChronoUnit.MINUTES));
    }

    public String createRefreshToken(Long userId, String phone) {
        Instant now = Instant.now();
        return createToken(userId, phone, TYPE_REFRESH, now,
                now.plus(properties.getRefreshTokenTtlDays(), ChronoUnit.DAYS));
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public Claims parseAccessToken(String token) {
        Claims claims = parse(token);
        return claims != null && TYPE_ACCESS.equals(claims.get("type", String.class)) ? claims : null;
    }

    public Claims parseRefreshToken(String token) {
        Claims claims = parse(token);
        return claims != null && TYPE_REFRESH.equals(claims.get("type", String.class)) ? claims : null;
    }

    private String createToken(Long userId, String phone, String type, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("phone", phone)
                .claim("type", type)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }
}
