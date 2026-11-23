package com.fixia.users.service;

import com.fixia.users.repository.OutboxEventRepository;
import com.fixia.users.repository.RefreshTokenRepository;
import com.fixia.users.repository.RevokedTokenRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cierra la sesión actual: invalida el access token en uso y el refresh token de esa sesión. Si el access token no
 * estaba revocado, escribe {@code sesion.revocada} en el Outbox en la misma transacción (GC-547): los demás servicios
 * deciden la revocación con esa lista, sin llamar a ms-users.
 */
@Service
public class LogoutService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OutboxEventRepository outboxRepository;
    private final SessionEvents sessionEvents;
    private final Clock clock;

    public LogoutService(RevokedTokenRepository revokedTokenRepository,
                         RefreshTokenRepository refreshTokenRepository, OutboxEventRepository outboxRepository,
                         SessionEvents sessionEvents, Clock clock) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.outboxRepository = outboxRepository;
        this.sessionEvents = sessionEvents;
        this.clock = clock;
    }

    /**
     * El refresh token solo se revoca si pertenece al usuario del access token. Un token desconocido,
     * ya revocado o de otra cuenta no produce error ni efecto: el cierre de sesión es idempotente y la
     * respuesta no permite averiguar qué tokens existen.
     */
    @Transactional
    public void logout(Jwt accessToken, String rawRefreshToken) {
        Instant now = clock.instant();
        UUID userId = TokenService.parseUserId(accessToken.getSubject());

        // Idempotente también ante cierres simultáneos del mismo token (GC-513): el segundo no rompe la llave.
        // Solo el cierre que revoca de verdad publica: un repetido no genera otro evento.
        if (revokedTokenRepository.revokeIfAbsent(accessToken.getId(), accessToken.getExpiresAt(), now) == 1) {
            outboxRepository.save(sessionEvents.revoked(UUID.fromString(accessToken.getId()), userId,
                    roleOf(accessToken), accessToken.getExpiresAt(), now));
        }

        refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .filter(stored -> stored.getUserId().equals(userId))
                .ifPresent(stored -> refreshTokenRepository.revokeIfActive(stored.getId(), now));
    }

    /** El rol del token sin el prefijo {@code ROLE_}; los tokens de ms-users llevan uno solo. */
    private static String roleOf(Jwt accessToken) {
        List<String> roles = accessToken.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalStateException("El token no trae su rol");
        }
        return roles.get(0).replaceFirst("^ROLE_", "");
    }
}
