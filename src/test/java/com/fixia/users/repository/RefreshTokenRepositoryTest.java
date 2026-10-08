package com.fixia.users.repository;

import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.RefreshToken;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Transaccional como en producción: la consulta de revocación se ejecuta dentro de la transacción del servicio. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void revocarEsAtomicoElSegundoIntentoNoRevocaNada() {
        Instant now = Instant.now();
        String uniqueSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(new User("ana-" + uniqueSuffix + "@example.com", "hash", Role.CLIENT, "Ana", "Pérez",
                DocumentType.CC, "123-" + uniqueSuffix, "300" + uniqueSuffix, "v1.0", now));
        String randomHash1 = (java.util.UUID.randomUUID().toString().replace("-", "") + "a".repeat(32)).substring(0, 64);
        RefreshToken token = refreshTokenRepository.save(
                new RefreshToken(user.getId(), randomHash1, now.plusSeconds(600), now));

        assertThat(refreshTokenRepository.revokeIfActive(token.getId(), now)).isEqualTo(1);
        assertThat(refreshTokenRepository.revokeIfActive(token.getId(), now)).isZero();
        assertThat(refreshTokenRepository.findById(token.getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void alBorrarElUsuarioSeBorranSusRefreshTokens() {
        Instant now = Instant.now();
        String uniqueSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(new User("ana-" + uniqueSuffix + "@example.com", "hash", Role.CLIENT, "Ana", "Pérez",
                DocumentType.CC, "123-" + uniqueSuffix, "300" + uniqueSuffix, "v1.0", now));
        String randomHash2 = (java.util.UUID.randomUUID().toString().replace("-", "") + "b".repeat(32)).substring(0, 64);
        refreshTokenRepository.save(new RefreshToken(user.getId(), randomHash2, now.plusSeconds(600), now));

        userRepository.deleteAll();
        // Sin flush, Hibernate no envía el DELETE antes de contar: son tablas distintas.
        userRepository.flush();

        assertThat(refreshTokenRepository.count()).isZero();
    }
}
