package com.fixia.users.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.RevokedToken;
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
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogoutControllerTest {

    private static final String LOGIN = "/api/users/auth/login";
    private static final String LOGOUT = "/api/users/auth/logout";
    private static final String REFRESH = "/api/users/auth/refresh";
    private static final String ME = "/api/users/me";
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
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        revokedTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void despuesDeCerrarSesionElAccessTokenYElRefreshTokenDejanDeServir() throws Exception {
        createUser("ana@example.com", "1020304050");
        JsonNode session = login("ana@example.com");

        me(session).andExpect(status().isOk());

        logout(session, session.get("refreshToken").asText()).andExpect(status().isNoContent());

        expectInvalidSession(me(session));
        mockMvc.perform(post(REFRESH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", session.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh token inválido o expirado"));
    }

    @Test
    void elAccessTokenRevocadoQuedaRegistradoConSuIdentificadorYExpiracion() throws Exception {
        createUser("ana@example.com", "1020304050");
        JsonNode session = login("ana@example.com");
        Jwt jwt = jwtDecoder.decode(session.get("accessToken").asText());

        logout(session, session.get("refreshToken").asText()).andExpect(status().isNoContent());

        RevokedToken revoked = revokedTokenRepository.findById(jwt.getId()).orElseThrow();
        assertThat(revoked.getExpiresAt()).isEqualTo(jwt.getExpiresAt());
        assertThat(revoked.getRevokedAt()).isNotNull();
    }

    @Test
    void cerrarSesionSinAccessTokenEsUnaSolicitudNoAutenticada() throws Exception {
        mockMvc.perform(post(LOGOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", "cualquiera"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void sinRefreshTokenRespondeConElCampoACorregirYLaSesionSigueActiva() throws Exception {
        createUser("ana@example.com", "1020304050");
        JsonNode session = login("ana@example.com");

        mockMvc.perform(post(LOGOUT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("refreshToken"));

        me(session).andExpect(status().isOk());
    }

    @Test
    void repetirElCierreDeSesionConElMismoTokenNoDaAccesoNiRevelaInformacion() throws Exception {
        createUser("ana@example.com", "1020304050");
        JsonNode session = login("ana@example.com");
        String refreshToken = session.get("refreshToken").asText();

        logout(session, refreshToken).andExpect(status().isNoContent());

        expectInvalidSession(logout(session, refreshToken));
    }

    @Test
    void cerrarSesionSoloAfectaALaSesionActualYNoALasDemasDelMismoUsuario() throws Exception {
        createUser("ana@example.com", "1020304050");
        JsonNode phone = login("ana@example.com");
        JsonNode laptop = login("ana@example.com");

        logout(phone, phone.get("refreshToken").asText()).andExpect(status().isNoContent());

        expectInvalidSession(me(phone));
        me(laptop).andExpect(status().isOk());
        mockMvc.perform(post(REFRESH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", laptop.get("refreshToken").asText()))))
                .andExpect(status().isOk());
    }

    @Test
    void unRefreshTokenAjenoNoSeRevocaPeroLaSesionPropiaSiSeCierra() throws Exception {
        createUser("ana@example.com", "1020304050");
        createUser("luis@example.com", "2030405060");
        JsonNode ana = login("ana@example.com");
        JsonNode luis = login("luis@example.com");

        logout(ana, luis.get("refreshToken").asText()).andExpect(status().isNoContent());

        expectInvalidSession(me(ana));
        mockMvc.perform(post(REFRESH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", luis.get("refreshToken").asText()))))
                .andExpect(status().isOk());
    }

    @Test
    void unRefreshTokenDesconocidoNoImpideCerrarLaSesion() throws Exception {
        createUser("ana@example.com", "1020304050");
        JsonNode session = login("ana@example.com");

        logout(session, "token-que-no-existe").andExpect(status().isNoContent());

        expectInvalidSession(me(session));
    }

    @Test
    void unAccessTokenSinJtiNoSePuedeVerificarYSeRechaza() throws Exception {
        User user = createUser("ana@example.com", "1020304050");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://auth.fixia.com")
                .subject("usr_" + user.getId())
                .audience(List.of("https://api.fixia.com"))
                .issuedAt(now).expiresAt(now.plusSeconds(600))
                .claim("roles", List.of("ROLE_CLIENT"))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Utilidades ----------

    private User createUser(String email, String documentNumber) {
        return userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), Role.CLIENT,
                "Ana", "Pérez", DocumentType.CC, documentNumber, "+573001112233", "v1.0", Instant.now()));
    }

    private JsonNode login(String email) throws Exception {
        String body = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private ResultActions logout(JsonNode session, String refreshToken) throws Exception {
        return mockMvc.perform(post(LOGOUT)
                .header(HttpHeaders.AUTHORIZATION, bearer(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refreshToken))));
    }

    private ResultActions me(JsonNode session) throws Exception {
        return mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(session)));
    }

    private static String bearer(JsonNode session) {
        return "Bearer " + session.get("accessToken").asText();
    }

    private String json(Map<String, String> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static void expectInvalidSession(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail").value("Se requiere autenticación válida"));
    }
}
