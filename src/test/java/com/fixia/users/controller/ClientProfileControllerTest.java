package com.fixia.users.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.repository.RefreshTokenRepository;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil del cliente (GC-317). Registro y login reales, para que las rutas se prueben con tokens JWT firmados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientProfileControllerTest {

    private static final String PROFILE = "/api/users/clients/me/profile";
    private static final String PASSWORD = "Clave1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        technicianRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- Utilidades ----------

    private void register(String path, String email, String document) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Ana");
        body.put("lastName", "Pérez");
        body.put("documentType", "CC");
        body.put("documentNumber", document);
        body.put("email", email);
        body.put("phone", "3001112233");
        body.put("password", PASSWORD);
        body.put("policyVersion", "v1.0");
        body.put("consentAccepted", true);
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/users/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private String clientToken(String email, String document) throws Exception {
        register("/api/users/clients", email, document);
        return login(email).get("accessToken").asText();
    }

    private ResultActions getProfile(String token) throws Exception {
        return mockMvc.perform(get(PROFILE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions updateProfile(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put(PROFILE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private static Map<String, Object> body(String firstName, String lastName, String phone) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("phone", phone);
        return body;
    }

    private User stored(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    // ---------- Consultar ----------

    @Test
    void elClienteConsultaSuPropioPerfil() throws Exception {
        String token = clientToken("ana@example.com", "123456789");

        getProfile(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stored("ana@example.com").getId().toString()))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.lastName").value("Pérez"))
                .andExpect(jsonPath("$.documentType").value("CC"))
                .andExpect(jsonPath("$.documentNumber").value("123456789"))
                .andExpect(jsonPath("$.phone").value("3001112233"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.consentPolicyVersion").doesNotExist());
    }

    @Test
    void cadaClienteVeSoloSuPerfil() throws Exception {
        String ana = clientToken("ana@example.com", "123456789");
        String luis = clientToken("luis@example.com", "987654321");

        getProfile(ana).andExpect(jsonPath("$.email").value("ana@example.com"));
        getProfile(luis).andExpect(jsonPath("$.email").value("luis@example.com"));
    }

    // ---------- Actualizar ----------

    @Test
    void losCambiosValidosQuedanPersistidosYSeMuestranAlVolverAConsultar() throws Exception {
        String token = clientToken("ana@example.com", "123456789");

        updateProfile(token, body("  María  ", " Gómez ", "+573104445566"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("María"))
                .andExpect(jsonPath("$.lastName").value("Gómez"))
                .andExpect(jsonPath("$.phone").value("+573104445566"));

        getProfile(token)
                .andExpect(jsonPath("$.firstName").value("María"))
                .andExpect(jsonPath("$.lastName").value("Gómez"))
                .andExpect(jsonPath("$.phone").value("+573104445566"));
        User saved = stored("ana@example.com");
        assertThat(saved.getFirstName()).isEqualTo("María");
        assertThat(saved.getPhone()).isEqualTo("+573104445566");
    }

    @Test
    void elCorreoElDocumentoElRolYElEstadoNoSePuedenCambiarDesdeElCuerpo() throws Exception {
        String token = clientToken("ana@example.com", "123456789");
        Map<String, Object> body = body("María", "Gómez", "3104445566");
        body.put("email", "otro@example.com");
        body.put("documentNumber", "555");
        body.put("documentType", "CE");
        body.put("role", "PROFESSIONAL");
        body.put("status", "DISABLED");
        body.put("id", UUID.randomUUID().toString());

        updateProfile(token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.documentNumber").value("123456789"))
                .andExpect(jsonPath("$.documentType").value("CC"));

        User saved = stored("ana@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.CLIENT);
        assertThat(saved.getEmail()).isEqualTo("ana@example.com");
        assertThat(saved.getDocumentNumber()).isEqualTo("123456789");
        getProfile(token).andExpect(status().isOk());
    }

    @Test
    void actualizarElPerfilNoCambiaElDeOtroCliente() throws Exception {
        String ana = clientToken("ana@example.com", "123456789");
        clientToken("luis@example.com", "987654321");

        updateProfile(ana, body("María", "Gómez", "3104445566")).andExpect(status().isOk());

        assertThat(stored("luis@example.com").getFirstName()).isEqualTo("Ana");
        assertThat(stored("luis@example.com").getPhone()).isEqualTo("3001112233");
    }

    @Test
    void losDatosInvalidosResponden400YNoCambianNada() throws Exception {
        String token = clientToken("ana@example.com", "123456789");

        updateProfile(token, body("", "Gómez", "3104445566"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("firstName")));
        updateProfile(token, body("María", "   ", "3104445566"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("lastName")));
        updateProfile(token, body("María", "Gómez", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("phone")));
        updateProfile(token, body("María", "Gómez", "123"))
                .andExpect(status().isBadRequest());
        updateProfile(token, body("M".repeat(101), "Gómez", "3104445566"))
                .andExpect(status().isBadRequest());
        updateProfile(token, body(null, null, null))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(PROFILE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("no es json"))
                .andExpect(status().isBadRequest());

        User saved = stored("ana@example.com");
        assertThat(saved.getFirstName()).isEqualTo("Ana");
        assertThat(saved.getLastName()).isEqualTo("Pérez");
        assertThat(saved.getPhone()).isEqualTo("3001112233");
    }

    @Test
    void elTelefonoConEspaciosSeRechazaPeroNombreYApellidoSeGuardanSinEllos() throws Exception {
        String token = clientToken("ana@example.com", "123456789");

        updateProfile(token, body("María", "Gómez", " 3104445566 "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("phone")));
        updateProfile(token, body("  María  ", "  Gómez  ", "3104445566"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("María"))
                .andExpect(jsonPath("$.lastName").value("Gómez"));
    }

    @Test
    void unaActualizacionSinCambiosEsValida() throws Exception {
        String token = clientToken("ana@example.com", "123456789");

        updateProfile(token, body("Ana", "Pérez", "3001112233"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana"));
    }

    // ---------- Autorización ----------

    @Test
    void sinTokenOConTokenInvalidoResponde401() throws Exception {
        mockMvc.perform(get(PROFILE)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(PROFILE).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("María", "Gómez", "3104445566"))))
                .andExpect(status().isUnauthorized());
        getProfile("token-invalido").andExpect(status().isUnauthorized());
    }

    @Test
    void unTecnicoNoPuedeUsarLasRutasDelPerfilDelCliente() throws Exception {
        register("/api/users/technicians", "carlos@example.com", "79800900");
        String token = login("carlos@example.com").get("accessToken").asText();

        getProfile(token).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
        updateProfile(token, body("María", "Gómez", "3104445566")).andExpect(status().isForbidden());

        assertThat(stored("carlos@example.com").getFirstName()).isEqualTo("Ana");
    }

    @Test
    void unClienteNoPuedeUsarLasRutasDelPerfilDelTecnico() throws Exception {
        String token = clientToken("ana@example.com", "123456789");

        mockMvc.perform(get("/api/users/technicians/me/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void conLaSesionCerradaOLaCuentaDeshabilitadaResponde401() throws Exception {
        register("/api/users/clients", "ana@example.com", "123456789");
        JsonNode session = login("ana@example.com");
        String token = session.get("accessToken").asText();
        getProfile(token).andExpect(status().isOk());

        mockMvc.perform(post("/api/users/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", session.get("refreshToken").asText()))))
                .andExpect(status().isNoContent());

        getProfile(token).andExpect(status().isUnauthorized());
        updateProfile(token, body("María", "Gómez", "3104445566")).andExpect(status().isUnauthorized());

        String second = login("ana@example.com").get("accessToken").asText();
        User ana = stored("ana@example.com");
        ana.disable();
        userRepository.saveAndFlush(ana);
        getProfile(second).andExpect(status().isUnauthorized());
        updateProfile(second, body("María", "Gómez", "3104445566")).andExpect(status().isUnauthorized());
    }
}
