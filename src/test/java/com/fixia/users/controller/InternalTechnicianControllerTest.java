package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.AvailabilitySlot;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta interna de elegibilidad (GC-342). La llamada de otro microservicio se simula con una autenticación interna
 * ya establecida: la prueba vale igual antes y después de que la rama tenga el filtro de clave compartida (GC-333).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalTechnicianControllerTest {

    /** 2026-10-19 es lunes: 14:00 UTC son las 09:00 en Bogotá; 23:00 UTC son las 18:00. */
    static final String MONDAY_9AM = "2026-10-19T14:00:00Z";
    static final String MONDAY_6PM = "2026-10-19T23:00:00Z";

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

    static RequestPostProcessor internalService() {
        return authentication(new UsernamePasswordAuthenticationToken("internal-service", null,
                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
    }

    private UUID user(String email, String document, Role role) {
        return userRepository.save(new User(email, "hash", role, "Carlos", "Gómez", DocumentType.CC, document,
                "3104445566", "v1.0", Instant.now())).getId();
    }

    /** Plomero en Bogotá (círculo de 10 km en el centro) que atiende los lunes de 08:00 a 12:00. */
    private UUID bogotaPlumber() {
        UUID userId = user("carlos@example.com", "79800900", Role.PROFESSIONAL);
        technicianRepository.save(new Technician(userId, Instant.now()));
        transactionTemplate.executeWithoutResult(status -> {
            Technician technician = technicianRepository.findByUserId(userId).orElseThrow();
            technician.replaceCategories(Set.of(ServiceCategory.PLUMBING), Instant.now());
            technician.updateCoverageZone(new CoverageZone("Bogotá", 4.65, -74.08, 10), Instant.now());
            technician.replaceAvailability(Set.of(new AvailabilitySlot(DayOfWeek.MONDAY, LocalTime.of(8, 0),
                    LocalTime.of(12, 0))), Instant.now());
        });
        return userId;
    }

    static Map<String, Object> request(UUID id, String category, String city, Double lat, Double lon, String at) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", id);
        data.put("category", category);
        data.put("city", city);
        data.put("latitude", lat);
        data.put("longitude", lon);
        data.put("expectedAt", at);
        return data;
    }

    private ResultActions evaluate(UUID userId, List<Map<String, Object>> requests) throws Exception {
        return mockMvc.perform(post("/internal/technicians/" + userId + "/eligibility").with(internalService())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("requests", requests))));
    }

    private ResultActions evaluateOne(UUID userId, Map<String, Object> request) throws Exception {
        return evaluate(userId, List.of(request));
    }

    // ---------- Reglas ----------

    @Test
    void unTecnicoConLaCategoriaLaZonaYElHorarioEsElegible() throws Exception {
        UUID technician = bogotaPlumber();
        UUID requestId = UUID.randomUUID();

        evaluateOne(technician, request(requestId, "PLUMBING", "bogota", 4.66, -74.06, MONDAY_9AM))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.results[0].requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.results[0].eligible").value(true))
                .andExpect(jsonPath("$.results[0].reasons", empty()));
    }

    @Test
    void cadaReglaIncumplidaAparececomoMotivo() throws Exception {
        UUID technician = bogotaPlumber();

        evaluateOne(technician, request(UUID.randomUUID(), "ELECTRICAL", "Bogotá", 4.66, -74.06, MONDAY_9AM))
                .andExpect(jsonPath("$.results[0].eligible").value(false))
                .andExpect(jsonPath("$.results[0].reasons", contains("CATEGORY")));
        evaluateOne(technician, request(UUID.randomUUID(), "PLUMBING", "Medellín", null, null, MONDAY_9AM))
                .andExpect(jsonPath("$.results[0].reasons", contains("ZONE")));
        evaluateOne(technician, request(UUID.randomUUID(), "PLUMBING", "Bogotá", 4.85, -74.06, MONDAY_9AM))
                .andExpect(jsonPath("$.results[0].reasons", contains("ZONE")));
        evaluateOne(technician, request(UUID.randomUUID(), "PLUMBING", "Bogotá", 4.66, -74.06, MONDAY_6PM))
                .andExpect(jsonPath("$.results[0].reasons", contains("AVAILABILITY")));
        evaluateOne(technician, request(UUID.randomUUID(), "PAINTING", "Cali", null, null, MONDAY_6PM))
                .andExpect(jsonPath("$.results[0].reasons", contains("CATEGORY", "ZONE", "AVAILABILITY")));
    }

    @Test
    void sinCoordenadasEnLaSolicitudBastaLaCiudad() throws Exception {
        UUID technician = bogotaPlumber();

        evaluateOne(technician, request(UUID.randomUUID(), "PLUMBING", "Bogotá", null, null, MONDAY_9AM))
                .andExpect(jsonPath("$.results[0].eligible").value(true));
    }

    @Test
    void unTecnicoSinPerfilCompletoNoEsElegibleYSabeQueLeFalta() throws Exception {
        UUID userId = user("nuevo@example.com", "11111111", Role.PROFESSIONAL);
        technicianRepository.save(new Technician(userId, Instant.now()));

        evaluateOne(userId, request(UUID.randomUUID(), "PLUMBING", "Bogotá", null, null, MONDAY_9AM))
                .andExpect(jsonPath("$.results[0].reasons", contains("CATEGORY", "ZONE", "AVAILABILITY")));
    }

    @Test
    void unUsuarioQueNoEsTecnicoActivoNoEsElegible() throws Exception {
        UUID client = user("cliente@example.com", "22222222", Role.CLIENT);
        UUID disabled = bogotaPlumber();
        transactionTemplate.executeWithoutResult(status -> userRepository.findById(disabled).orElseThrow().disable());

        for (UUID userId : List.of(client, disabled, UUID.randomUUID())) {
            evaluateOne(userId, request(UUID.randomUUID(), "PLUMBING", "Bogotá", null, null, MONDAY_9AM))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].eligible").value(false))
                    .andExpect(jsonPath("$.results[0].reasons", contains("TECHNICIAN_NOT_FOUND")));
        }
    }

    @Test
    void evaluaVariasSolicitudesEnUnaSolaLlamadaYEnElMismoOrden() throws Exception {
        UUID technician = bogotaPlumber();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();

        evaluate(technician, List.of(
                request(first, "PLUMBING", "Bogotá", null, null, MONDAY_9AM),
                request(second, "CARPENTRY", "Bogotá", null, null, MONDAY_9AM),
                request(third, "PLUMBING", "Bogotá", null, null, MONDAY_6PM)))
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].requestId").value(first.toString()))
                .andExpect(jsonPath("$.results[0].eligible").value(true))
                .andExpect(jsonPath("$.results[1].reasons", contains("CATEGORY")))
                .andExpect(jsonPath("$.results[2].reasons", contains("AVAILABILITY")));
    }

    // ---------- Validación y seguridad ----------

    @Test
    void unaConsultaMalFormadaResponde400() throws Exception {
        UUID technician = bogotaPlumber();

        evaluate(technician, List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("requests"));
        evaluateOne(technician, request(null, null, " ", null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4));
        evaluateOne(technician, request(UUID.randomUUID(), "GARDENING", "Bogotá", null, null, MONDAY_9AM))
                .andExpect(status().isBadRequest());
        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            tooMany.add(request(UUID.randomUUID(), "PLUMBING", "Bogotá", null, null, MONDAY_9AM));
        }
        evaluate(technician, tooMany).andExpect(status().isBadRequest());
    }

    @Test
    void sinAutenticacionResponde401() throws Exception {
        mockMvc.perform(post("/internal/technicians/" + UUID.randomUUID() + "/eligibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requests\":[]}"))
                .andExpect(status().isUnauthorized());
    }
}
