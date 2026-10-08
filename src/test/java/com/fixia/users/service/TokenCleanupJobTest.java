package com.fixia.users.service;

import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.RefreshToken;
import com.fixia.users.domain.RevokedToken;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.repository.RefreshTokenRepository;
import com.fixia.users.repository.RevokedTokenRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TokenCleanupJobTest {

    @Autowired
    private TokenCleanupJob cleanupJob;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    @BeforeEach
    void cleanDatabase() {
        revokedTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void eliminaSoloLosTokensExpirados() {
        Instant now = Instant.now();
        User user = userRepository.save(new User("ana@example.com", "hash", Role.CLIENT, "Ana", "Pérez",
                DocumentType.CC, "123", "3001112233", "v1.0", now));

        refreshTokenRepository.save(new RefreshToken(user.getId(), "a".repeat(64), now.minusSeconds(60), now.minus(Duration.ofDays(8))));
        refreshTokenRepository.save(new RefreshToken(user.getId(), "b".repeat(64), now.plus(Duration.ofDays(1)), now));
        revokedTokenRepository.save(new RevokedToken("jti-vencido", now.minusSeconds(60), now.minusSeconds(900)));
        revokedTokenRepository.save(new RevokedToken("jti-vigente", now.plusSeconds(600), now));

        cleanupJob.cleanup();

        assertThat(refreshTokenRepository.findByTokenHash("a".repeat(64))).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("b".repeat(64))).isPresent();
        assertThat(revokedTokenRepository.existsById("jti-vencido")).isFalse();
        assertThat(revokedTokenRepository.existsById("jti-vigente")).isTrue();
    }
}
