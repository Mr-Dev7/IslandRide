package com.islandride.core.security;

import com.islandride.identityaccess.domain.RefreshToken;
import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${islandride.security.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public static class RotationResult {
        private final String rawNewToken;
        private final User user;

        public RotationResult(String rawNewToken, User user) {
            this.rawNewToken = rawNewToken;
            this.user = user;
        }

        public String getRawNewToken() {
            return rawNewToken;
        }

        public User getUser() {
            return user;
        }
    }

    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = hashToken(rawToken);
        String familyId = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setFamilyId(familyId);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(Instant.now().plusMillis(refreshTokenExpirationMs));
        refreshToken.setRevoked(false);

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public RotationResult rotateRefreshToken(String rawOldToken) {
        if (rawOldToken == null || rawOldToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token cannot be blank");
        }

        String oldHash = hashToken(rawOldToken);
        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(oldHash)
                .orElseThrow(() -> new SecurityException("Refresh token not found or invalid"));

        // Detect reuse of an already revoked token -> Replay attack on token family
        if (oldToken.isRevoked()) {
            log.warn("Security Alert: Replay attack detected on refresh token family: {}. Revoking entire family.", oldToken.getFamilyId());
            refreshTokenRepository.revokeFamily(oldToken.getFamilyId(), Instant.now());
            throw new SecurityException("Revoked refresh token presented. Token family has been invalidated.");
        }

        // Check expiration
        if (oldToken.getExpiresAt().isBefore(Instant.now())) {
            oldToken.setRevoked(true);
            oldToken.setRevokedAt(Instant.now());
            refreshTokenRepository.save(oldToken);
            throw new SecurityException("Refresh token has expired");
        }

        // Token is valid: Revoke old token and issue replacement within the same family
        String rawNewToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String newHash = hashToken(rawNewToken);

        oldToken.setRevoked(true);
        oldToken.setRevokedAt(Instant.now());
        oldToken.setReplacedByToken(newHash);
        refreshTokenRepository.save(oldToken);

        RefreshToken newToken = new RefreshToken();
        newToken.setUser(oldToken.getUser());
        newToken.setFamilyId(oldToken.getFamilyId()); // Retain family tracking
        newToken.setTokenHash(newHash);
        newToken.setExpiresAt(Instant.now().plusMillis(refreshTokenExpirationMs));
        newToken.setRevoked(false);
        refreshTokenRepository.save(newToken);

        return new RotationResult(rawNewToken, oldToken.getUser());
    }

    @Transactional
    public void revokeRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String hash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
