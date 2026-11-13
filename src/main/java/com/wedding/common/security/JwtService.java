package com.wedding.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class JwtService {
    private final SecretKey key;
    private final long expireMinutes;

    public JwtService(@Value("${wedding.jwt.secret}") String secret,
                      @Value("${wedding.jwt.expire-minutes:720}") long expireMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
    }

    public String issue(Long userId, Long tenantId, String tenantCode, String username,
                        String userType, Long storeId, String platformRole) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of(
                        "tenantId", tenantId == null ? 0L : tenantId,
                        "tenantCode", tenantCode == null ? "" : tenantCode,
                        "username", username,
                        "userType", userType,
                        "storeId", storeId == null ? 0L : storeId,
                        "platformRole", platformRole == null ? "" : platformRole))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
