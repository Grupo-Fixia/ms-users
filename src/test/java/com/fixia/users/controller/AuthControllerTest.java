package com.fixia.users.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.RefreshToken;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.repository.RefreshTokenRepository;
import com.fixia.users.repository.UserRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
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
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String LOGIN = "/api/users/auth/login";
    private static final String REFRESH = "/api/users/auth/refresh";
    private static final String ME = "/api/users/me";
    private static final String EMAIL = "ana.perez@example.com";
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- Inicio de sesión ----------

    @Test
    void conCredencialesValidasIniciaSesionYEmiteUnAccessTokenRS256() throws Exception {
        User user = createUser(EMAIL, PASSWORD);

        JsonNode tokens = login(EMAIL, PASSWORD);

        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();

        Jwt jwt = jwtDecoder.decode(tokens.get("accessToken").asText());
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(jwt.getSubject()).isEqualTo("usr_" + user.getId());
        assertThat(jwt.getIssuer()).hasToString("https://auth.fixia.com");
        assertThat(jwt.getAudience()).containsExactly("https://api.fixia.com");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.<List<String>>getClaim("roles")).containsExactly("ROLE_CLIENT");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void elCorreoSeNormalizaAlIniciarSesion() throws Exception {
        createUser(EMAIL, PASSWORD);

        login("ANA.Perez@Example.com", PASSWORD);
    }

    @Test
    void elRefreshTokenSoloSeGuardaComoHash() throws Exception {
        createUser(EMAIL, PASSWORD);

        String refreshToken = login(EMAIL, PASSWORD).get("refreshToken").asText();

        List<RefreshToken> stored = refreshTokenRepository.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getTokenHash()).isNotEqualTo(refreshToken).isEqualTo(sha256(refreshToken));
        assertThat(stored.get(0).getRevokedAt()).isNull();
    }

    @Test
    void conContrasenaIncorrectaOCorreoInexistenteLaRespuestaEsIdenticaYNoRevelaNada() throws Exception {
        createUser(EMAIL, PASSWORD);

        String wrongPassword = attemptLogin(EMAIL, "OtraClave999")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String unknownEmail = attemptLogin("nadie@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(unknownEmail).isEqualTo(wrongPassword);
        assertThat(wrongPassword).doesNotContain(EMAIL).doesNotContain("contraseña incorrecta");
    }

    @Test
    void unaCuentaDeshabilitadaNoPuedeIniciarSesionYRecibeElMismoError() throws Exception {
        User user = createUser(EMAIL, PASSWORD);
        user.disable();
        userRepository.save(user);

        attemptLogin(EMAIL, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
    }

    @Test
    void sinCorreoOContrasenaRespondeConLosCamposACorregir() throws Exception {
        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    // ---------- Rutas privadas ----------

    @Test
    void sinTokenNoSePuedeAccederALaInformacionPrivada() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail").value("Se requiere autenticación válida"));
    }

    @Test
    void conUnAccessTokenValidoSeAccedeAlPerfilSinDatosSensibles() throws Exception {
        createUser(EMAIL, PASSWORD);
        String accessToken = login(EMAIL, PASSWORD).get("accessToken").asText();

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    void unTokenExpiradoSeRechaza() throws Exception {
        User user = createUser(EMAIL, PASSWORD);
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        String token = sign(jwtEncoder, claims(user, "https://auth.fixia.com", "https://api.fixia.com",
                issuedAt, issuedAt.plus(Duration.ofMinutes(15))));

        expectUnauthorized(token);
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() throws Exception {
        User user = createUser(EMAIL, PASSWORD);
        JwtEncoder foreignEncoder = encoderWithNewKey();
        String token = sign(foreignEncoder, validClaims(user));

        expectUnauthorized(token);
    }

    @Test
    void unTokenAlteradoSeRechaza() throws Exception {
        createUser(EMAIL, PASSWORD);
        String valid = login(EMAIL, PASSWORD).get("accessToken").asText();
        String[] parts = valid.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"usr_00000000-0000-0000-0000-000000000000\",\"roles\":[\"ROLE_ADMIN\"]}"
                        .getBytes(StandardCharsets.UTF_8));

        expectUnauthorized(parts[0] + "." + forgedPayload + "." + parts[2]);
    }

    @Test
    void unTokenSinFirmaAlgNoneSeRechaza() throws Exception {
        User user = createUser(EMAIL, PASSWORD);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"sub\":\"usr_" + user.getId()
                + "\",\"iss\":\"https://auth.fixia.com\",\"aud\":[\"https://api.fixia.com\"]}")
                .getBytes(StandardCharsets.UTF_8));

        expectUnauthorized(header + "." + payload + ".");
    }

    @Test
    void unTokenConEmisorOAudienciaDistintosSeRechaza() throws Exception {
        User user = createUser(EMAIL, PASSWORD);
        Instant now = Instant.now();

        expectUnauthorized(sign(jwtEncoder, claims(user, "https://otro-emisor.example", "https://api.fixia.com",
                now, now.plusSeconds(600))));
        expectUnauthorized(sign(jwtEncoder, claims(user, "https://auth.fixia.com", "https://otra-audiencia.example",
                now, now.plusSeconds(600))));
    }

    @Test
    void unTokenBasuraSeRechaza() throws Exception {
        expectUnauthorized("esto-no-es-un-jwt");
    }

    @Test
    void unTokenValidoDeUnUsuarioInexistenteOSubjectMalformadoSeRechaza() throws Exception {
        Instant now = Instant.now();
        User ghost = new User("fantasma@example.com", "x", Role.CLIENT, "A", "B", DocumentType.CC, "1", "3000000",
                "v1", now);

        expectUnauthorized(sign(jwtEncoder, validClaims(ghost)));

        JwtClaimsSet malformed = JwtClaimsSet.builder()
                .issuer("https://auth.fixia.com").subject("sin-formato")
                .audience(List.of("https://api.fixia.com"))
                .issuedAt(now).expiresAt(now.plusSeconds(600)).id(UUID.randomUUID().toString())
                .claim("roles", List.of("ROLE_CLIENT")).build();
        expectUnauthorized(sign(jwtEncoder, malformed));
    }

    @Test
    void siLaCuentaSeDeshabilitaElAccessTokenYaNoDaAccesoAlPerfil() throws Exception {
        User user = createUser(EMAIL, PASSWORD);
        String accessToken = login(EMAIL, PASSWORD).get("accessToken").asText();
        user.disable();
        userRepository.save(user);

        expectUnauthorized(accessToken);
    }

    // ---------- Refresh ----------

    @Test
    void elRefreshEmiteUnParNuevoYElTokenAnteriorDejaDeServir() throws Exception {
        createUser(EMAIL, PASSWORD);
        JsonNode first = login(EMAIL, PASSWORD);
        String oldRefresh = first.get("refreshToken").asText();

        JsonNode second = objectMapper.readTree(refresh(oldRefresh)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(second.get("refreshToken").asText()).isNotEqualTo(oldRefresh);
        assertThat(second.get("accessToken").asText()).isNotEqualTo(first.get("accessToken").asText());

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + second.get("accessToken").asText()))
                .andExpect(status().isOk());

        refresh(oldRefresh)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh token inválido o expirado"));
    }

    @Test
    void unRefreshTokenDesconocidoSeRechaza() throws Exception {
        refresh("token-que-no-existe").andExpect(status().isUnauthorized());
    }

    @Test
    void unRefreshTokenExpiradoSeRechaza() throws Exception {
        User user = createUser(EMAIL, PASSWORD);
        String raw = "refresh-expirado";
        Instant now = Instant.now();
        refreshTokenRepository.save(new RefreshToken(user.getId(), sha256(raw), now.minusSeconds(60),
                now.minus(Duration.ofDays(8))));

        refresh(raw).andExpect(status().isUnauthorized());
    }

    @Test
    void elRefreshTokenDeUnaCuentaDeshabilitadaSeRechaza() throws Exception {
        User user = createUser(EMAIL, PASSWORD);
        String refreshToken = login(EMAIL, PASSWORD).get("refreshToken").asText();
        user.disable();
        userRepository.save(user);

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void sinRefreshTokenRespondeConElCampoACorregir() throws Exception {
        mockMvc.perform(post(REFRESH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("refreshToken"));
    }

    // ---------- Utilidades ----------

    private User createUser(String email, String rawPassword) {
        return userRepository.save(new User(email, passwordEncoder.encode(rawPassword), Role.CLIENT,
                "Ana", "Pérez", DocumentType.CC, "1020304050", "+573001112233", "v1.0", Instant.now()));
    }

    private ResultActions attemptLogin(String email, String password) throws Exception {
        return mockMvc.perform(post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = attemptLogin(email, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post(REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private void expectUnauthorized(String bearerToken) throws Exception {
        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail").value("Se requiere autenticación válida"));
    }

    private static JwtClaimsSet validClaims(User user) {
        Instant now = Instant.now();
        return claims(user, "https://auth.fixia.com", "https://api.fixia.com", now, now.plusSeconds(600));
    }

    private static JwtClaimsSet claims(User user, String issuer, String audience, Instant issuedAt, Instant expiresAt) {
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("usr_" + user.getId())
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of("ROLE_CLIENT"))
                .build();
    }

    private static String sign(JwtEncoder encoder, JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static JwtEncoder encoderWithNewKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("clave-ajena")
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
