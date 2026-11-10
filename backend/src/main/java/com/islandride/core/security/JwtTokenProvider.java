package com.islandride.core.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey key;
    private final long accessTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${islandride.security.jwt.secret}") String secret,
            @Value("${islandride.security.jwt.access-token-expiration-ms:900000}") long accessTokenExpirationMs) {
        this.accessTokenExpirationMs = accessTokenExpirationMs;

        byte[] keyBytes;
        try {
            // Attempt Base64 decoding if the secret is hex/base64 encoded
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            // Ensure minimum 256-bit key length for HS256
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpirationMs);

        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        var builder = Jwts.builder()
                .subject(principal.getPublicId())
                .claim("userId", principal.getId())
                .claim("email", principal.getEmail())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));

        if (principal.getBusinessId() != null) {
            builder.claim("businessId", principal.getBusinessId());
        }

        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String getPublicId(String token) {
        return getClaims(token).getSubject();
    }

    public Long getUserId(String token) {
        Number userId = getClaims(token).get("userId", Number.class);
        return userId != null ? userId.longValue() : null;
    }

    public String getEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return getClaims(token).get("roles", List.class);
    }

    public Long getBusinessId(String token) {
        Number businessId = getClaims(token).get("businessId", Number.class);
        return businessId != null ? businessId.longValue() : null;
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }
}
