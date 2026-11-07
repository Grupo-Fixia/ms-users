package com.fixia.users.repository;

import com.fixia.users.domain.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    /**
     * Registra el token revocado si todavía no lo está (GC-513). Varios cierres de sesión simultáneos con el mismo
     * token llegan a esta inserción a la vez: con {@code ON CONFLICT DO NOTHING} el primero la hace y los demás no
     * fallan, en lugar de romper la llave primaria. Se conserva la primera fecha de revocación.
     *
     * @return 1 si se registró ahora, 0 si ya estaba revocado
     */
    @Modifying
    @Query(value = "INSERT INTO revoked_tokens (jti, expires_at, revoked_at) VALUES (:jti, :expiresAt, :revokedAt) "
            + "ON CONFLICT DO NOTHING", nativeQuery = true)
    int revokeIfAbsent(@Param("jti") String jti, @Param("expiresAt") Instant expiresAt,
                       @Param("revokedAt") Instant revokedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RevokedToken r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
