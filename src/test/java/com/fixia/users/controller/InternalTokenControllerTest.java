package com.fixia.users.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.repository.RefreshTokenRepository;
import com.fixia.users.repository.RevokedTokenRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta interna de revocación que usan otros microservicios. Se prueba con el flujo real
 * (login → logout) para comprobar que lo que consulta el otro servicio es lo que escribe el cierre de sesión.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalTokenControllerTest {

    private static final String LOGIN = "/api/users/auth/login";
    private static final String LOGOUT = "/api/users/auth/logout";
    private static final String ME = "/api/users/me";
    private static final String PASSWORD = "Clave1234";
    private static final String KEY_HEADER = "X-Internal-Api-Key";
    private static final String KEY = "clave-interna-de-prueba";

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

    @BeforeEach
    void cleanDatabase() {
        revokedTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void unTokenVigenteSeInformaComoNoRevocado() throws Exception {
        createUser();
        JsonNode session = login();

        statusOf(jtiOf(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(false));
    }

    @Test
    void despuesDelLogoutElTokenSeInformaComoRevocado() throws Exception {
        createUser();
        JsonNode session = login();
        String jti = jtiOf(session);

        mockMvc.perform(post(LOGOUT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", session.get("refreshToken").asText()))))
                .andExpect(status().isNoContent());

        statusOf(jti).andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(true));
    }

    @Test
    void unIdentificadorDesconocidoRespondeNoRevocadoYNoNotFound() throws Exception {
        statusOf("jti-que-no-existe").andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(false));
    }

    @Test
    void laRespuestaNoRevelaDatosDelUsuarioNiSePuedeCachear() throws Exception {
        createUser();
        JsonNode session = login();

        statusOf(jtiOf(session))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.revoked").exists());
    }

    @Test
    void sinLaClaveInternaLaPeticionEsNoAutenticada() throws Exception {
        mockMvc.perform(get("/internal/tokens/cualquiera/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail").value("Se requiere autenticación válida"));
    }

    @Test
    void unaClaveIncorrectaOVaciaSeRechaza() throws Exception {
        mockMvc.perform(get("/internal/tokens/cualquiera/status").header(KEY_HEADER, "otra-clave"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/tokens/cualquiera/status").header(KEY_HEADER, ""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenDeUsuarioValidoNoSirveParaLasRutasInternas() throws Exception {
        createUser();
        JsonNode session = login();

        mockMvc.perform(get("/internal/tokens/" + jtiOf(session) + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void laClaveInternaNoDaAccesoALasRutasDeUsuarios() throws Exception {
        mockMvc.perform(get(ME).header(KEY_HEADER, KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lasRutasDeUsuariosSiguenFuncionandoConSuTokenSinClaveInterna() throws Exception {
        createUser();
        JsonNode session = login();

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + session.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    // ---------- Utilidades ----------

    private ResultActions statusOf(String jti) throws Exception {
        return mockMvc.perform(get("/internal/tokens/" + jti + "/status").header(KEY_HEADER, KEY));
    }

    private String jtiOf(JsonNode session) {
        return jwtDecoder.decode(session.get("accessToken").asText()).getId();
    }

    private void createUser() {
        userRepository.save(new User("ana@example.com", passwordEncoder.encode(PASSWORD), Role.CLIENT,
                "Ana", "Pérez", DocumentType.CC, "1020304050", "+573001112233", "v1.0", Instant.now()));
    }

    private JsonNode login() throws Exception {
        String body = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "ana@example.com", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
