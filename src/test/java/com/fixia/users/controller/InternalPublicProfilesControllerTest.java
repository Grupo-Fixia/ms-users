package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.CoverageZone;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil público de los técnicos para ms-services (GC-425). La llamada de otro microservicio se simula con la
 * autenticación interna ya establecida; la clave compartida real se prueba al final.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalPublicProfilesControllerTest {

    private static final String PATH = "/internal/technicians/public-profiles";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
    }

    // ---------- Utilidades ----------

    private UUID user(String email, String document, Role role, String firstName, String lastName) {
        return userRepository.save(new User(email, "hash", role, firstName, lastName, DocumentType.CC, document,
                "3104445566", "v1.0", Instant.now())).getId();
    }

    /** Técnico con perfil completo: plomero y electricista en Bogotá, con 8 años de experiencia. */
    private UUID completeTechnician() {
        UUID userId = user("carlos@example.com", "79800900", Role.PROFESSIONAL, "Carlos", "Gómez");
        technicianRepository.save(new Technician(userId, Instant.now()));
        transactionTemplate.executeWithoutResult(status -> {
            Technician technician = technicianRepository.findByUserId(userId).orElseThrow();
            technician.updateProfile("Plomero con experiencia en fugas y redes de agua", (short) 8,
                    Set.of(ServiceCategory.PLUMBING, ServiceCategory.ELECTRICAL), Instant.now());
            technician.updateCoverageZone(new CoverageZone("Bogotá", 4.65, -74.08, 10), Instant.now());
        });
        return userId;
    }

    /** Técnico recién registrado: sin descripción, experiencia, especialidades ni zona. */
    private UUID newTechnician(String email, String document) {
        UUID userId = user(email, document, Role.PROFESSIONAL, "Ana", "Ruiz");
        technicianRepository.save(new Technician(userId, Instant.now()));
        return userId;
    }

    private ResultActions find(List<?> userIds) throws Exception {
        return mockMvc.perform(post(PATH).with(InternalTechnicianControllerTest.internalService())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("userIds", userIds))));
    }

    // ---------- Contenido ----------

    @Test
    void devuelveElPerfilPublicoDelTecnico() throws Exception {
        UUID technician = completeTechnician();

        find(List.of(technician))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.profiles", hasSize(1)))
                .andExpect(jsonPath("$.profiles[0].userId").value(technician.toString()))
                .andExpect(jsonPath("$.profiles[0].displayName").value("Carlos G."))
                .andExpect(jsonPath("$.profiles[0].verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.profiles[0].categories", contains("PLUMBING", "ELECTRICAL")))
                .andExpect(jsonPath("$.profiles[0].professionalDescription")
                        .value("Plomero con experiencia en fugas y redes de agua"))
                .andExpect(jsonPath("$.profiles[0].yearsOfExperience").value(8))
                .andExpect(jsonPath("$.profiles[0].city").value("Bogotá"));
    }

    @Test
    void noExponeDatosPrivadosNiLaUbicacionDeLaZona() throws Exception {
        UUID technician = completeTechnician();

        find(List.of(technician))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("carlos@example.com"))))
                .andExpect(content().string(not(containsString("79800900"))))
                .andExpect(content().string(not(containsString("3104445566"))))
                .andExpect(content().string(not(containsString("Gómez"))))
                .andExpect(jsonPath("$.profiles[0].email").doesNotExist())
                .andExpect(jsonPath("$.profiles[0].phone").doesNotExist())
                .andExpect(jsonPath("$.profiles[0].latitude").doesNotExist())
                .andExpect(jsonPath("$.profiles[0].radiusKm").doesNotExist());
    }

    @Test
    void unPerfilIncompletoLlegaConCamposVaciosYNoInventados() throws Exception {
        UUID technician = newTechnician("ana@example.com", "52000111");

        find(List.of(technician))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles[0].displayName").value("Ana R."))
                .andExpect(jsonPath("$.profiles[0].categories", empty()))
                .andExpect(jsonPath("$.profiles[0].professionalDescription").value(nullValue()))
                .andExpect(jsonPath("$.profiles[0].yearsOfExperience").value(nullValue()))
                .andExpect(jsonPath("$.profiles[0].city").value(nullValue()));
    }

    @Test
    void respetaElOrdenPedidoYOmiteRepetidosClientesDeshabilitadosEInexistentes() throws Exception {
        UUID first = completeTechnician();
        UUID second = newTechnician("ana@example.com", "52000111");
        UUID client = user("cliente@example.com", "1000222333", Role.CLIENT, "Laura", "Pérez");
        UUID disabled = newTechnician("pedro@example.com", "80111222");
        transactionTemplate.executeWithoutResult(status -> userRepository.findById(disabled).orElseThrow().disable());

        find(List.of(second, client, first, UUID.randomUUID(), disabled, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles[*].userId", contains(second.toString(), first.toString())));
    }

    // ---------- Validación y seguridad ----------

    @Test
    void unaListaVaciaOConMasDeCienTecnicosResponde400() throws Exception {
        find(List.of()).andExpect(status().isBadRequest());

        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(UUID.randomUUID());
        }
        find(tooMany).andExpect(status().isBadRequest());
    }

    @Test
    void sinLaClaveInternaNoResponde() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("userIds", List.of(completeTechnician())));

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH).header("X-Internal-Api-Key", "otra-clave")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH).header("X-Internal-Api-Key", "clave-interna-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles", hasSize(1)));
    }
}
