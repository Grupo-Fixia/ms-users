package com.fixia.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Access token invalidado antes de su expiración (cierre de sesión). Se guarda solo el identificador "jti"
 * y la fecha de expiración del token, para poder borrar la fila cuando el token ya no valdría de todos modos.
 */
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

    @Id
    @Column(length = 64)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    protected RevokedToken() {
    }

    public RevokedToken(String jti, Instant expiresAt, Instant revokedAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public String getJti() {
        return jti;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
