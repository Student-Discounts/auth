package com.vk.studentdiscounts.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /**
     * Блокировка строки не даёт двум параллельным запросам обменять один и тот же refresh-токен.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    Optional<UserSession> findByPreviousRefreshTokenHash(String previousRefreshTokenHash);

    Optional<UserSession> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            select s from UserSession s
            where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now
            order by s.lastUsedAt desc
            """)
    List<UserSession> findActive(UUID userId, Instant now);

    @Modifying
    @Query("""
            update UserSession s set s.revokedAt = :now, s.revokeReason = :reason
            where s.userId = :userId and s.revokedAt is null
            """)
    int revokeAll(UUID userId, SessionRevokeReason reason, Instant now);
}
