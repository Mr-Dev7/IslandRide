package com.islandride.identityaccess.repository;

import com.islandride.identityaccess.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByFamilyId(String familyId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :revokedAt WHERE rt.familyId = :familyId AND rt.revoked = false")
    int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :revokedAt WHERE rt.user.id = :userId AND rt.revoked = false")
    int revokeAllUserTokens(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
