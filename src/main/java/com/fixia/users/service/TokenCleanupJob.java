package com.fixia.users.service;

import com.fixia.users.repository.RefreshTokenRepository;
import com.fixia.users.repository.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Borra periódicamente los tokens que ya expiraron: un token vencido se rechaza igual por su fecha. */
@Component
public class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);

    private final RevokedTokenRepository revokedTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public TokenCleanupJob(RevokedTokenRepository revokedTokenRepository,
                           RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${fixia.cleanup.interval:PT1H}",
            initialDelayString = "${fixia.cleanup.interval:PT1H}")
    @Transactional
    public void cleanup() {
        Instant now = clock.instant();
        int revoked = revokedTokenRepository.deleteExpired(now);
        int refresh = refreshTokenRepository.deleteExpired(now);
        log.info("Limpieza de tokens: {} revocados y {} refresh tokens expirados eliminados", revoked, refresh);
    }
}
