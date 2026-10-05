package com.fixia.users.repository;

import com.fixia.users.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoca el token solo si sigue activo. La condición atómica garantiza que un refresh token
     * sea de un solo uso aunque lleguen dos solicitudes simultáneas; devuelve 0 si ya estaba revocado.
     * <p>
     * {@code flushAutomatically} es imprescindible junto a {@code clearAutomatically}: sin él, los cambios
     * pendientes de la misma transacción (por ejemplo, un token revocado por logout) se descartan sin guardarse.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    int revokeIfActive(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
