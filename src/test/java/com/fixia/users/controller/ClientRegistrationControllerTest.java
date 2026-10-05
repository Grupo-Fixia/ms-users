package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientRegistrationControllerTest {

    private static final String URL = "/api/users/clients";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Ana");
        body.put("lastName", "Pérez");
        body.put("documentType", "CC");
        body.put("documentNumber", "1020304050");
        body.put("email", "ana.perez@example.com");
        body.put("phone", "+573001112233");
        body.put("password", "Clave1234");
        body.put("policyVersion", "v1.0");
        body.put("consentAccepted", true);
        return body;
    }

    private ResultActions register(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    @Test
    void registraClienteConRolClienteYNoExponeLaContrasena() throws Exception {
        register(validBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("ana.perez@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("Clave1234"))));

        User saved = userRepository.findByEmail("ana.perez@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.CLIENT);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getDocumentType()).isEqualTo(DocumentType.CC);
    }

    @Test
    void guardaLaContrasenaConBcryptCosto12() throws Exception {
        register(validBody()).andExpect(status().isCreated());

        User saved = userRepository.findByEmail("ana.perez@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2a$12$");
        assertThat(passwordEncoder.matches("Clave1234", saved.getPasswordHash())).isTrue();
    }

    @Test
    void registraElConsentimientoConFechaYVersionDeLaPolitica() throws Exception {
        Instant before = Instant.now().minusSeconds(1);

        register(validBody()).andExpect(status().isCreated());

        User saved = userRepository.findByEmail("ana.perez@example.com").orElseThrow();
        assertThat(saved.getConsentPolicyVersion()).isEqualTo("v1.0");
        assertThat(saved.getConsentAcceptedAt()).isBetween(before, Instant.now().plus(Duration.ofSeconds(1)));
    }

    @Test
    void normalizaElCorreoAMinusculas() throws Exception {
        Map<String, Object> body = validBody();
        body.put("email", "Ana.Perez@Example.COM");

        register(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana.perez@example.com"));
    }

    @Test
    void sinDatosObligatoriosNoCreaLaCuentaEIndicaQueCorregir() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("firstName")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("lastName")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("documentType")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("documentNumber")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("phone")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("policyVersion")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("consentAccepted")));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void rechazaSiNoSeAceptaElTratamientoDeDatos() throws Exception {
        Map<String, Object> body = validBody();
        body.put("consentAccepted", false);

        register(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("consentAccepted"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void rechazaCorreoInvalido() throws Exception {
        Map<String, Object> body = validBody();
        body.put("email", "no-es-un-correo");

        register(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void rechazaContrasenaDebil() throws Exception {
        Map<String, Object> body = validBody();
        body.put("password", "sololetras");

        register(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void rechazaTipoDeDocumentoDesconocido() throws Exception {
        Map<String, Object> body = validBody();
        body.put("documentType", "OTRO");

        register(body).andExpect(status().isBadRequest());
    }

    @Test
    void rechazaJsonMalformado() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{no es json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechazaCorreoDuplicadoSinImportarMayusculas() throws Exception {
        register(validBody()).andExpect(status().isCreated());

        Map<String, Object> other = validBody();
        other.put("email", "ANA.PEREZ@example.com");
        other.put("documentNumber", "99999999");

        register(other).andExpect(status().isConflict());
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void rechazaDocumentoDuplicado() throws Exception {
        register(validBody()).andExpect(status().isCreated());

        Map<String, Object> other = validBody();
        other.put("email", "otra@example.com");

        register(other).andExpect(status().isConflict());
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void elRegistroNoRequiereAutenticacionPeroElRestoSi() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
