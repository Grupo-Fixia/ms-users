package com.fixia.users.service;

import com.fixia.users.domain.RevokedToken;
import com.fixia.users.repository.RefreshTokenRepository;
import com.fixia.users.repository.RevokedTokenRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Cierra la sesión actual: invalida el access token en uso y el refresh token de esa sesión. */
@Service
public class LogoutService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public LogoutService(RevokedTokenRepository revokedTokenRepository,
                         RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
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

        revokedTokenRepository.save(new RevokedToken(accessToken.getId(), accessToken.getExpiresAt(), now));

        refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .filter(stored -> stored.getUserId().equals(userId))
                .ifPresent(stored -> refreshTokenRepository.revokeIfActive(stored.getId(), now));
    }
}
