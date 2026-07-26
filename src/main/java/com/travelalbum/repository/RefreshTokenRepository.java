package com.travelalbum.repository;

import com.travelalbum.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    @Modifying
    @Transactional
    @Query("""
UPDATE RefreshToken r
SET r.revoked = true
WHERE r.userId = :userId
AND r.revoked = false
""")
    void revokeAllByUserId(@Param("userId") Long userId);
}
