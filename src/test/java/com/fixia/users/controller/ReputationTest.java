package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.EvaluationDirection;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.ServiceEvaluation;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.repository.ServiceEvaluationRepository;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import com.fixia.users.service.ReputationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reputación real (GC-446): promedio y número de las evaluaciones que los clientes le dieron al técnico, calculados al
 * consultar a partir de {@code service_evaluations} (opción A del contrato GC-443 §7). Se ve en el perfil público que
 * ms-services pide para los postulantes y en el perfil propio del técnico.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReputationTest {

    private static final String PUBLIC_PROFILES = "/internal/technicians/public-profiles";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private ServiceEvaluationRepository evaluationRepository;

    @Autowired
    private ReputationService reputationService;

    private int documents;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        evaluationRepository.deleteAllInBatch();
        technicianRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    // ---------- Utilidades ----------

    private UUID user(Role role, String firstName) {
        documents++;
        return userRepository.save(new User(firstName.toLowerCase() + documents + "@example.com", "hash", role,
                firstName, "Gómez", DocumentType.CC, "7980090" + documents, "3104445566", "v1.0",
                Instant.now())).getId();
    }

    private UUID technician(String firstName) {
        UUID userId = user(Role.PROFESSIONAL, firstName);
        technicianRepository.save(new Technician(userId, Instant.now()));
        return userId;
    }

    /** Una evaluación ya registrada (la API de GC-444 tiene sus propias pruebas), de un servicio cerrado distinto. */
    private void evaluation(EvaluationDirection direction, UUID evaluator, UUID evaluated, int rating, String comment) {
        Instant closedAt = Instant.now().minusSeconds(3600);
        evaluationRepository.save(ServiceEvaluation.register(UUID.randomUUID(), direction, evaluator, evaluated, rating,
                comment, closedAt, Instant.now()));
    }

    private void clientRates(UUID technician, int... ratings) {
        for (int rating : ratings) {
            evaluation(EvaluationDirection.CLIENT_TO_PROVIDER, user(Role.CLIENT, "Laura"), technician, rating, null);
        }
    }

    private ResultActions publicProfiles(UUID... userIds) throws Exception {
        return mockMvc.perform(post(PUBLIC_PROFILES).with(InternalTechnicianControllerTest.internalService())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("userIds", List.of(userIds)))));
    }

    // ---------- Perfil público (postulantes de ms-services) ----------

    @Test
    void sinEvaluacionesNoHayReputacionNiEstrellasInventadas() throws Exception {
        UUID technician = technician("Carlos");

        publicProfiles(technician)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles[0].userId").value(technician.toString()))
                .andExpect(jsonPath("$.profiles[0].reputation").value(nullValue()));
    }

    @Test
    void laPrimeraEvaluacionDaSuNotaYUnConteoDeUno() throws Exception {
        UUID technician = technician("Carlos");
        clientRates(technician, 5);

        publicProfiles(technician)
                .andExpect(jsonPath("$.profiles[0].reputation.average").value(5.0))
                .andExpect(jsonPath("$.profiles[0].reputation.count").value(1));
    }

    @Test
    void variasEvaluacionesDanElPromedioRedondeadoAUnDecimalConLaMitadHaciaArriba() throws Exception {
        UUID twoThirds = technician("Carlos");
        clientRates(twoThirds, 5, 4, 4);          // 13 / 3 = 4,333… → 4,3
        UUID half = technician("Ana");
        clientRates(half, 5, 4, 4, 4);            // 17 / 4 = 4,25 → 4,3 (mitad hacia arriba)
        UUID low = technician("Pedro");
        clientRates(low, 1, 2);                   // 3 / 2 = 1,5

        publicProfiles(twoThirds, half, low)
                .andExpect(jsonPath("$.profiles[0].reputation.average").value(4.3))
                .andExpect(jsonPath("$.profiles[0].reputation.count").value(3))
                .andExpect(jsonPath("$.profiles[1].reputation.average").value(4.3))
                .andExpect(jsonPath("$.profiles[1].reputation.count").value(4))
                .andExpect(jsonPath("$.profiles[2].reputation.average").value(1.5))
                .andExpect(jsonPath("$.profiles[2].reputation.count").value(2));
    }

    @Test
    void lasEvaluacionesQueElTecnicoDaOQueRecibeComoClienteNoCuentan() throws Exception {
        UUID technician = technician("Carlos");
        UUID client = user(Role.CLIENT, "Laura");
        clientRates(technician, 4);
        // El técnico evalúa a un cliente: es la reputación del cliente, no la suya.
        evaluation(EvaluationDirection.PROVIDER_TO_CLIENT, technician, client, 1, null);

        publicProfiles(technician)
                .andExpect(jsonPath("$.profiles[0].reputation.average").value(4.0))
                .andExpect(jsonPath("$.profiles[0].reputation.count").value(1));
    }

    @Test
    void cadaTecnicoDelLoteRecibeSoloSuReputacion() throws Exception {
        UUID rated = technician("Carlos");
        UUID unrated = technician("Ana");
        clientRates(rated, 3, 5);

        publicProfiles(unrated, rated)
                .andExpect(jsonPath("$.profiles[0].userId").value(unrated.toString()))
                .andExpect(jsonPath("$.profiles[0].reputation").value(nullValue()))
                .andExpect(jsonPath("$.profiles[1].reputation.average").value(4.0))
                .andExpect(jsonPath("$.profiles[1].reputation.count").value(2));
    }

    @Test
    void elPerfilPublicoNoExponeComentariosNiQuienEvaluo() throws Exception {
        UUID technician = technician("Carlos");
        UUID client = user(Role.CLIENT, "Laura");
        evaluation(EvaluationDirection.CLIENT_TO_PROVIDER, client, technician, 5, "Muy puntual, dejó todo limpio");

        publicProfiles(technician)
                .andExpect(jsonPath("$.profiles[0].reputation.count").value(1))
                .andExpect(content().string(not(containsString("puntual"))))
                .andExpect(content().string(not(containsString(client.toString()))));
    }

    @Test
    void laReputacionSiempreCoincideConLasEvaluacionesGuardadas() throws Exception {
        // No hay copia: consultar dos veces, o después de una nueva evaluación, lee la fuente de verdad.
        UUID technician = technician("Carlos");
        clientRates(technician, 2);
        publicProfiles(technician).andExpect(jsonPath("$.profiles[0].reputation.average").value(2.0));
        publicProfiles(technician).andExpect(jsonPath("$.profiles[0].reputation.count").value(1));

        clientRates(technician, 5);

        publicProfiles(technician)
                .andExpect(jsonPath("$.profiles[0].reputation.average").value(3.5))
                .andExpect(jsonPath("$.profiles[0].reputation.count").value(2));
    }

    // ---------- Perfil propio del técnico ----------

    private ResultActions ownProfile(UUID technician) throws Exception {
        return mockMvc.perform(get("/api/users/technicians/me/profile")
                .with(jwt().jwt(token -> token.subject("usr_" + technician))
                        .authorities(new SimpleGrantedAuthority("ROLE_PROFESSIONAL"))));
    }

    @Test
    void elTecnicoVeEnSuPerfilLaReputacionQueVenLosClientes() throws Exception {
        UUID technician = technician("Carlos");
        ownProfile(technician)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reputation").value(nullValue()));

        clientRates(technician, 5, 4);

        ownProfile(technician)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reputation.average").value(4.5))
                .andExpect(jsonPath("$.reputation.count").value(2));
    }

    // ---------- Reputación del cliente (se calcula, no se muestra en este sprint) ----------

    @Test
    void laReputacionDelClienteSaleDeLoQueLeDieronLosTecnicos() {
        UUID technician = technician("Carlos");
        UUID client = user(Role.CLIENT, "Laura");
        evaluation(EvaluationDirection.PROVIDER_TO_CLIENT, technician, client, 3, null);
        evaluation(EvaluationDirection.CLIENT_TO_PROVIDER, client, technician, 5, null);

        assertThat(reputationService.asClient(List.of(client)).get(client).count()).isEqualTo(1);
        assertThat(reputationService.asClient(List.of(client)).get(client).average()).isEqualByComparingTo("3.0");
        assertThat(reputationService.asClient(List.of(technician))).isEmpty();
        assertThat(reputationService.asProvider(List.of())).isEmpty();
    }
}
