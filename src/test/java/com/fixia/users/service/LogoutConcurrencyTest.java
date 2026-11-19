package com.fixia.users.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.RevokedToken;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.repository.RefreshTokenRepository;
import com.fixia.users.repository.RevokedTokenRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GC-513 (DEF-QA-01): varios cierres de sesión simultáneos con el mismo token pasan todos la validación antes de que
 * el primero confirme, y cada uno intenta registrar el mismo {@code jti} en {@code revoked_tokens}. Antes, el segundo
 * rompía la llave primaria y respondía 409 «Cuenta existente»; el cierre de sesión debe ser idempotente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogoutConcurrencyTest {

    private static final String LOGIN = "/api/users/auth/login";
    private static final String LOGOUT = "/api/users/auth/logout";
    private static final String PASSWORD = "Clave1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private LogoutService logoutService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    @BeforeEach
    void cleanDatabase() {
        revokedTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    /**
     * Reproduce la carrera de forma determinista: otra transacción ya insertó el mismo {@code jti} y aún no confirma
     * cuando llega este cierre de sesión, que por eso no lo ve. Al confirmar la primera, este no debe fallar.
     */
    @Test
    void unCierreDeSesionQueCoincideConOtroEnCursoNoFalla() throws Exception {
        JsonNode session = loginNewUser();
        Jwt jwt = jwtDecoder.decode(session.get("accessToken").asText());
        Instant firstRevokedAt = Instant.now().minusSeconds(5).truncatedTo(ChronoUnit.MICROS);

        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> other = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            revokedTokenRepository.saveAndFlush(new RevokedToken(jwt.getId(), jwt.getExpiresAt(), firstRevokedAt));
            inserted.countDown();
            await(release);
        }));
        assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();

        Future<?> concurrent = executor.submit(
                () -> logoutService.logout(jwt, session.get("refreshToken").asText()));
        Thread.sleep(300);
        release.countDown();

        other.get(15, TimeUnit.SECONDS);
        assertThatCode(() -> concurrent.get(15, TimeUnit.SECONDS)).doesNotThrowAnyException();

        assertThat(revokedTokenRepository.count()).isEqualTo(1);
        RevokedToken revoked = revokedTokenRepository.findById(jwt.getId()).orElseThrow();
        assertThat(revoked.getRevokedAt()).isEqualTo(firstRevokedAt);
        assertThat(revoked.getExpiresAt()).isEqualTo(jwt.getExpiresAt());
    }

    /** Lo que reportó QA: cinco cierres simultáneos con los mismos tokens. Ninguno responde 409. */
    @Test
    void variosCierresDeSesionSimultaneosNoRespondenConflicto() throws Exception {
        for (int round = 0; round < 5; round++) {
            JsonNode session = loginNewUser();
            String body = objectMapper.writeValueAsString(
                    Map.of("refreshToken", session.get("refreshToken").asText()));
            String bearer = "Bearer " + session.get("accessToken").asText();

            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> responses = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Callable<Integer> logout = () -> {
                    await(start);
                    return mockMvc.perform(post(LOGOUT)
                                    .header(HttpHeaders.AUTHORIZATION, bearer)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn().getResponse().getStatus();
                };
                responses.add(executor.submit(logout));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> response : responses) {
                statuses.add(response.get(30, TimeUnit.SECONDS));
            }
            // 204 para quien cerró la sesión; 401 si llegó cuando el token ya estaba revocado. Nunca 409 ni 500.
            assertThat(statuses).allMatch(code -> code == 204 || code == 401).contains(204);
            assertThat(refreshTokenRepository.findAll()).allMatch(token -> token.getRevokedAt() != null);
        }
    }

    private JsonNode loginNewUser() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String email = "ana" + suffix + "@example.com";
        userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), Role.CLIENT, "Ana", "Pérez",
                DocumentType.CC, suffix.substring(suffix.length() - 10), "+573001112233", "v1.0", Instant.now()));
        String body = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Tiempo de espera agotado");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
