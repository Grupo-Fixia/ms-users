package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.domain.VerificationStatus;
import com.fixia.users.repository.TechnicianRepository;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TechnicianRegistrationControllerTest {

    private static final String URL = "/api/users/technicians";
    private static final String EMAIL = "carlos.gomez@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        technicianRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Carlos");
        body.put("lastName", "Gómez");
        body.put("documentType", "CC");
        body.put("documentNumber", "79800900");
        body.put("email", EMAIL);
        body.put("phone", "+573104445566");
        body.put("password", "Tecnico123");
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
    void registraTecnicoConRolProfessionalYVerificacionPendiente() throws Exception {
        register(validBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.technicianId").isNotEmpty())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("Tecnico123"))));

        User saved = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.PROFESSIONAL);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(passwordEncoder.matches("Tecnico123", saved.getPasswordHash())).isTrue();

        Technician technician = technicianRepository.findByUserId(saved.getId()).orElseThrow();
        assertThat(technician.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(technician.getCreatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void registraElConsentimientoConFechaYVersionDeLaPolitica() throws Exception {
        Instant before = Instant.now().minusSeconds(1);

        register(validBody()).andExpect(status().isCreated());

        User saved = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(saved.getConsentPolicyVersion()).isEqualTo("v1.0");
        assertThat(saved.getConsentAcceptedAt()).isBetween(before, Instant.now().plus(Duration.ofSeconds(1)));
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
        assertThat(technicianRepository.count()).isZero();
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
    void rechazaCorreoYaRegistradoComoClienteSinCrearElTecnico() throws Exception {
        Map<String, Object> client = validBody();
        client.put("documentNumber", "11111111");
        mockMvc.perform(post("/api/users/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(client)))
                .andExpect(status().isCreated());

        Map<String, Object> technician = validBody();
        technician.put("email", "Carlos.Gomez@Example.com");

        register(technician).andExpect(status().isConflict());
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(technicianRepository.count()).isZero();
    }

    @Test
    void rechazaDocumentoDuplicado() throws Exception {
        register(validBody()).andExpect(status().isCreated());

        Map<String, Object> other = validBody();
        other.put("email", "otro.tecnico@example.com");

        register(other).andExpect(status().isConflict());
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(technicianRepository.count()).isEqualTo(1);
    }

    @Test
    void elTecnicoRegistradoPuedeIniciarSesionConRolProfessional() throws Exception {
        register(validBody()).andExpect(status().isCreated());

        mockMvc.perform(post("/api/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", "Tecnico123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
}
