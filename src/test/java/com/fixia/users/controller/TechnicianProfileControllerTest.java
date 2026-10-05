package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TechnicianProfileControllerTest {

    private static final String PROFILE = "/api/users/technicians/me/profile";
    private static final String PASSWORD = "Tecnico123";

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

    // ---------- Utilidades ----------

    private UUID registerTechnician(String email, String documentNumber) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Carlos");
        body.put("lastName", "Gómez");
        body.put("documentType", "CC");
        body.put("documentNumber", documentNumber);
        body.put("email", email);
        body.put("phone", "+573104445566");
        body.put("password", PASSWORD);
        body.put("policyVersion", "v1.0");
        body.put("consentAccepted", true);
        String response = mockMvc.perform(post("/api/users/technicians")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private Map<String, Object> validProfile() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("professionalDescription", "Plomero con experiencia en redes hidráulicas residenciales.");
        body.put("yearsOfExperience", 8);
        body.put("categories", List.of("PLUMBING", "MAINTENANCE"));
        return body;
    }

    private ResultActions updateProfile(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put(PROFILE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions getProfile(String token) throws Exception {
        return mockMvc.perform(get(PROFILE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    // ---------- Registrar, consultar y actualizar ----------

    @Test
    void elTecnicoRecienRegistradoTieneUnPerfilVacioPendienteDeVerificacion() throws Exception {
        registerTechnician("carlos@example.com", "79800900");

        getProfile(login("carlos@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianId").isNotEmpty())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.professionalDescription").isEmpty())
                .andExpect(jsonPath("$.yearsOfExperience").isEmpty())
                .andExpect(jsonPath("$.categories").isEmpty());
    }

    @Test
    void elTecnicoRegistraSuInformacionProfesionalYLaPuedeConsultar() throws Exception {
        UUID userId = registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");

        updateProfile(token, validProfile())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalDescription")
                        .value("Plomero con experiencia en redes hidráulicas residenciales."))
                .andExpect(jsonPath("$.yearsOfExperience").value(8))
                .andExpect(jsonPath("$.categories", containsInAnyOrder("PLUMBING", "MAINTENANCE")));

        getProfile(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearsOfExperience").value(8))
                .andExpect(jsonPath("$.categories", containsInAnyOrder("PLUMBING", "MAINTENANCE")));

        Technician saved = technicianRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getCategories()).containsExactlyInAnyOrder(ServiceCategory.PLUMBING, ServiceCategory.MAINTENANCE);
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(saved.getCreatedAt());
    }

    @Test
    void alActualizarConservaQuitaYAgregaCategoriasSinDuplicarlas() throws Exception {
        UUID userId = registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");
        updateProfile(token, validProfile()).andExpect(status().isOk());

        Map<String, Object> changed = validProfile();
        changed.put("professionalDescription", "  Plomero y electricista.  ");
        changed.put("yearsOfExperience", 9);
        changed.put("categories", List.of("PLUMBING", "ELECTRICAL"));

        updateProfile(token, changed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalDescription").value("Plomero y electricista."))
                .andExpect(jsonPath("$.yearsOfExperience").value(9))
                .andExpect(jsonPath("$.categories", containsInAnyOrder("PLUMBING", "ELECTRICAL")));

        assertThat(technicianRepository.findByUserId(userId).orElseThrow().getCategories())
                .containsExactlyInAnyOrder(ServiceCategory.PLUMBING, ServiceCategory.ELECTRICAL);
    }

    @Test
    void conDatosInvalidosNoActualizaEIndicaQueCorregir() throws Exception {
        registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");

        updateProfile(token, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("professionalDescription")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("yearsOfExperience")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("categories")));

        Map<String, Object> negative = validProfile();
        negative.put("yearsOfExperience", -1);
        updateProfile(token, negative)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("yearsOfExperience"));
    }

    @Test
    void rechazaCategoriaDesconocida() throws Exception {
        registerTechnician("carlos@example.com", "79800900");
        Map<String, Object> body = validProfile();
        body.put("categories", List.of("JARDINERIA"));

        updateProfile(login("carlos@example.com"), body).andExpect(status().isBadRequest());
    }

    // ---------- Autorización ----------

    @Test
    void cadaTecnicoSoloVeYModificaSuPropioPerfil() throws Exception {
        UUID carlos = registerTechnician("carlos@example.com", "79800900");
        UUID laura = registerTechnician("laura@example.com", "52600700");

        updateProfile(login("laura@example.com"), validProfile()).andExpect(status().isOk());

        getProfile(login("carlos@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isEmpty());
        assertThat(technicianRepository.findByUserId(carlos).orElseThrow().getCategories()).isEmpty();
        assertThat(technicianRepository.findByUserId(laura).orElseThrow().getCategories()).hasSize(2);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(PROFILE)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(PROFILE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validProfile())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unClienteAutenticadoNoPuedeUsarElPerfilDeTecnico() throws Exception {
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("firstName", "Ana");
        client.put("lastName", "Pérez");
        client.put("documentType", "CC");
        client.put("documentNumber", "1020304050");
        client.put("email", "cliente@example.com");
        client.put("phone", "+573001112233");
        client.put("password", PASSWORD);
        client.put("policyVersion", "v1.0");
        client.put("consentAccepted", true);
        mockMvc.perform(post("/api/users/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(client)))
                .andExpect(status().isCreated());
        String token = login("cliente@example.com");

        getProfile(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
        updateProfile(token, validProfile()).andExpect(status().isForbidden());
        assertThat(technicianRepository.count()).isZero();
    }

    @Test
    void unaCuentaProfessionalSinPerfilDeTecnicoResponde404() throws Exception {
        userRepository.save(new User("sinperfil@example.com", passwordEncoder.encode(PASSWORD), Role.PROFESSIONAL,
                "Sin", "Perfil", DocumentType.CC, "33333333", "3001112233", "v1.0", Instant.now()));

        getProfile(login("sinperfil@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Perfil no encontrado"));
    }
}
