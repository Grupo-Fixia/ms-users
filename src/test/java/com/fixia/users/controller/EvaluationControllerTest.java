package com.fixia.users.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.client.ServiceParticipationClient;
import com.fixia.users.client.ServiceParticipationClient.Participation;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.OutboxEvent;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.ServiceEvaluation;
import com.fixia.users.domain.User;
import com.fixia.users.exception.ServicesUnavailableException;
import com.fixia.users.repository.OutboxEventRepository;
import com.fixia.users.repository.ServiceEvaluationRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Evaluaciones bidireccionales (GC-444, contrato de GC-443). ms-services se simula con un doble del cliente de
 * participación; la base, la restricción única y el Outbox son los reales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvaluationControllerTest {

    private static final String PATH = "/api/users/evaluations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceEvaluationRepository evaluationRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @MockBean
    private ServiceParticipationClient participationClient;

    private UUID client;
    private UUID technician;
    private UUID otherClient;
    private UUID otherTechnician;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAllInBatch();
        // El borrado en cascada se lleva también las evaluaciones.
        userRepository.deleteAllInBatch();
        client = account("cliente@example.com", "100", Role.CLIENT);
        technician = account("tecnico@example.com", "200", Role.PROFESSIONAL);
        otherClient = account("otro-cliente@example.com", "300", Role.CLIENT);
        otherTechnician = account("otro-tecnico@example.com", "400", Role.PROFESSIONAL);
    }

    // ---------- Utilidades ----------

    private UUID account(String email, String document, Role role) {
        return userRepository.save(new User(email, "hash", role, "Laura", "Gómez", DocumentType.CC, document,
                "3104445566", "v1.0", Instant.now())).getId();
    }

    private static RequestPostProcessor as(UUID userId, String role) {
        return jwt().jwt(token -> token.subject("usr_" + userId)).authorities(new SimpleGrantedAuthority(role));
    }

    /** ms-services responde que el servicio está cerrado hace {@code closedAgo} entre {@code client} y {@code technician}. */
    private UUID closedService(Duration closedAgo) {
        UUID id = UUID.randomUUID();
        participation(new Participation(id, "CLOSED", client, technician, "INDEPENDENT",
                Instant.now().minus(closedAgo)));
        return id;
    }

    private UUID closedService() {
        return closedService(Duration.ofHours(1));
    }

    private void participation(Participation participation) {
        doReturn(Optional.of(participation)).when(participationClient).find(participation.serviceRequestId());
    }

    private ResultActions evaluate(UUID userId, String role, Object serviceRequestId, Object rating, String comment)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("serviceRequestId", serviceRequestId);
        body.put("rating", rating);
        if (comment != null) {
            body.put("comment", comment);
        }
        return mockMvc.perform(post(PATH).with(as(userId, role)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions asClient(UUID serviceRequestId, Object rating, String comment) throws Exception {
        return evaluate(client, "ROLE_CLIENT", serviceRequestId, rating, comment);
    }

    private JsonNode onlyEvent() throws Exception {
        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        return objectMapper.readTree(events.get(0).getPayload());
    }

    // ---------- Registrar ----------

    @Test
    void elClienteEvaluaAlTecnicoYSeGuardaConSuEvento() throws Exception {
        UUID service = closedService();

        asClient(service, 5, "  Puntual y ordenado  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceRequestId").value(service.toString()))
                .andExpect(jsonPath("$.direction").value("CLIENT_TO_PROVIDER"))
                .andExpect(jsonPath("$.evaluatorId").value(client.toString()))
                .andExpect(jsonPath("$.evaluatedId").value(technician.toString()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Puntual y ordenado"))
                .andExpect(jsonPath("$.source").value("CONFIRMED_CLOSURE"));

        ServiceEvaluation saved = evaluationRepository.findAll().get(0);
        OutboxEvent outbox = outboxRepository.findAll().get(0);
        assertThat(outbox.getTopic()).isEqualTo("fixia.users.evaluations");
        assertThat(outbox.getMessageKey()).isEqualTo(technician.toString());
        assertThat(outbox.getAggregateType()).isEqualTo("Evaluation");
        assertThat(outbox.getAggregateId()).isEqualTo(saved.getId());
        assertThat(outbox.getAggregateSequence()).isEqualTo(1);
        assertThat(outbox.getStatus().name()).isEqualTo("PENDING");

        JsonNode event = onlyEvent();
        assertThat(event.get("eventId").asText()).isEqualTo(outbox.getEventId().toString());
        assertThat(event.get("eventType").asText()).isEqualTo("evaluacion.registrada");
        assertThat(event.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(event.get("producer").asText()).isEqualTo("ms-users");
        assertThat(event.at("/aggregate/id").asText()).isEqualTo(saved.getId().toString());
        assertThat(event.at("/aggregate/sequence").asInt()).isEqualTo(1);
        assertThat(event.get("correlationId").asText()).isEqualTo(service.toString());
        assertThat(event.at("/actor/userId").asText()).isEqualTo(client.toString());
        assertThat(event.at("/actor/role").asText()).isEqualTo("CLIENT");
        assertThat(event.at("/data/evaluationId").asText()).isEqualTo(saved.getId().toString());
        assertThat(event.at("/data/direction").asText()).isEqualTo("CLIENT_TO_PROVIDER");
        assertThat(event.at("/data/evaluatedId").asText()).isEqualTo(technician.toString());
        assertThat(event.at("/data/rating").asInt()).isEqualTo(5);
        assertThat(event.at("/data/source").asText()).isEqualTo("CONFIRMED_CLOSURE");
        assertThat(event.at("/data/serviceClosedAt").asText())
                .isEqualTo(saved.getServiceClosedAt().toString());
        // Nunca el comentario: puede traer datos personales.
        assertThat(event.get("data").has("comment")).isFalse();
        assertThat(outbox.getPayload()).doesNotContain("Puntual");
    }

    @Test
    void elTecnicoEvaluaAlClienteSinComentario() throws Exception {
        UUID service = closedService();

        evaluate(technician, "ROLE_PROFESSIONAL", service, 4, "   ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("PROVIDER_TO_CLIENT"))
                .andExpect(jsonPath("$.evaluatorId").value(technician.toString()))
                .andExpect(jsonPath("$.evaluatedId").value(client.toString()))
                .andExpect(jsonPath("$.comment").value(nullValue()));
        assertThat(onlyEvent().at("/actor/role").asText()).isEqualTo("PROFESSIONAL");
    }

    @Test
    void lasDosDireccionesConvivenEnElMismoServicio() throws Exception {
        UUID service = closedService();

        asClient(service, 5, null).andExpect(status().isCreated());
        evaluate(technician, "ROLE_PROFESSIONAL", service, 3, null).andExpect(status().isCreated());

        assertThat(evaluationRepository.count()).isEqualTo(2);
        assertThat(outboxRepository.count()).isEqualTo(2);
    }

    @Test
    void repetirLaMismaEvaluacionRespondeLaExistenteSinOtroEvento() throws Exception {
        UUID service = closedService();
        String id = objectMapper.readTree(asClient(service, 5, "Muy bien").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        asClient(service, 5, "  Muy bien ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        assertThat(evaluationRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void unReintentoIgualSeResponde200AunqueYaHayaVencidoElPlazo() throws Exception {
        UUID service = closedService(Duration.ofHours(1));
        asClient(service, 4, null).andExpect(status().isCreated());
        participation(new Participation(service, "CLOSED", client, technician, "INDEPENDENT",
                Instant.now().minus(Duration.ofDays(10))));

        asClient(service, 4, null).andExpect(status().isOk());
    }

    @Test
    void unaEvaluacionDistintaEnLaMismaDireccionResponde409() throws Exception {
        UUID service = closedService();
        asClient(service, 5, "Muy bien").andExpect(status().isCreated());

        asClient(service, 2, "Muy bien")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasons", contains("EVALUATION_ALREADY_EXISTS")));
        asClient(service, 5, "Cambié de opinión").andExpect(status().isConflict());
        asClient(service, 5, null).andExpect(status().isConflict());

        assertThat(evaluationRepository.findAll()).extracting(ServiceEvaluation::getRating).containsExactly(5);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    // ---------- Quién puede evaluar ----------

    @Test
    void unTerceroOUnServicioInexistenteResponde404() throws Exception {
        UUID service = closedService();
        doReturn(Optional.empty()).when(participationClient).find(any());
        participation(new Participation(service, "CLOSED", client, technician, "INDEPENDENT", Instant.now()));

        evaluate(otherClient, "ROLE_CLIENT", service, 5, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Servicio no encontrado"));
        evaluate(otherTechnician, "ROLE_PROFESSIONAL", service, 5, null).andExpect(status().isNotFound());
        // El cliente no puede evaluar con un token de técnico ni al revés.
        evaluate(client, "ROLE_PROFESSIONAL", service, 5, null).andExpect(status().isNotFound());
        evaluate(technician, "ROLE_CLIENT", service, 5, null).andExpect(status().isNotFound());
        asClient(UUID.randomUUID(), 5, null).andExpect(status().isNotFound());

        assertThat(evaluationRepository.count()).isZero();
    }

    @Test
    void unaEmpresaNoEvaluaNiConsulta() throws Exception {
        UUID company = account("empresa@example.com", "500", Role.COMPANY);
        UUID service = closedService();

        evaluate(company, "ROLE_COMPANY", service, 5, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
        mockMvc.perform(get(PATH).param("serviceRequestIds", service.toString()).with(as(company, "ROLE_COMPANY")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(participationClient);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH).param("serviceRequestIds", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unSubjectMalFormadoResponde401() throws Exception {
        mockMvc.perform(post(PATH).with(jwt().jwt(token -> token.subject("sin-prefijo"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceRequestId\":\"" + UUID.randomUUID() + "\",\"rating\":5}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Reglas ----------

    @Test
    void unServicioQueNoEstaCerradoResponde422() throws Exception {
        UUID service = UUID.randomUUID();
        participation(new Participation(service, "TECHNICIAN_FINISHED", client, technician, "INDEPENDENT", null));

        asClient(service, 5, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Evaluación no permitida"))
                .andExpect(jsonPath("$.reasons", contains("SERVICE_NOT_CLOSED")));

        UUID withoutProvider = UUID.randomUUID();
        participation(new Participation(withoutProvider, "PUBLISHED", client, null, null, null));
        asClient(withoutProvider, 5, null)
                .andExpect(jsonPath("$.reasons", contains("SERVICE_NOT_CLOSED")));
    }

    @Test
    void unServicioDeEmpresaNoSeEvalua() throws Exception {
        UUID company = account("empresa@example.com", "500", Role.COMPANY);
        UUID service = UUID.randomUUID();
        participation(new Participation(service, "CLOSED", client, company, "COMPANY", Instant.now()));

        asClient(service, 5, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reasons", contains("COMPANY_SERVICE_NOT_EVALUABLE")));
    }

    @Test
    void pasadoElPlazoDeDosDiasNoSeEvalua() throws Exception {
        UUID late = closedService(Duration.ofDays(2).plusMinutes(1));
        asClient(late, 5, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reasons", contains("EVALUATION_WINDOW_EXPIRED")));

        UUID inTime = closedService(Duration.ofDays(2).minusMinutes(1));
        asClient(inTime, 5, null).andExpect(status().isCreated());
    }

    @Test
    void siElEvaluadoYaNoExisteResponde422() throws Exception {
        UUID service = UUID.randomUUID();
        participation(new Participation(service, "CLOSED", client, UUID.randomUUID(), "INDEPENDENT",
                Instant.now()));

        asClient(service, 5, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reasons", contains("EVALUATED_USER_NOT_FOUND")));
    }

    @Test
    void siMsServicesNoRespondeResponde503SinGuardarNada() throws Exception {
        UUID service = UUID.randomUUID();
        doThrow(new ServicesUnavailableException("caído", null)).when(participationClient).find(service);

        asClient(service, 5, null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Servicio no disponible"));
        assertThat(evaluationRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void validaLaNotaYElComentario() throws Exception {
        UUID service = closedService();

        for (Object rating : new Object[]{0, 6, 4.5, -1, "cinco"}) {
            asClient(service, rating, null).andExpect(status().isBadRequest());
        }
        asClient(service, null, null).andExpect(status().isBadRequest());
        asClient(service, 5, "x".repeat(501)).andExpect(status().isBadRequest());
        evaluate(client, "ROLE_CLIENT", null, 5, null).andExpect(status().isBadRequest());
        evaluate(client, "ROLE_CLIENT", "no-es-un-uuid", 5, null).andExpect(status().isBadRequest());
        assertThat(evaluationRepository.count()).isZero();

        asClient(service, 5, "x".repeat(500)).andExpect(status().isCreated());
    }

    // ---------- Concurrencia ----------

    private List<Integer> concurrently(List<Callable<Integer>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void variosEnviosIgualesALaVezDejanUnaSolaEvaluacionYUnSoloEvento() throws Exception {
        UUID service = closedService();

        List<Integer> statuses = concurrently(IntStream.range(0, 6)
                .<Callable<Integer>>mapToObj(i -> () -> asClient(service, 5, "Bien").andReturn().getResponse()
                        .getStatus())
                .toList());

        assertThat(statuses).containsOnly(200, 201);
        assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
        assertThat(evaluationRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void variosEnviosDistintosALaVezDejanUnaSolaEvaluacionYLosDemasReciben409() throws Exception {
        UUID service = closedService();

        List<Integer> statuses = concurrently(IntStream.rangeClosed(1, 5)
                .<Callable<Integer>>mapToObj(rating -> () -> asClient(service, rating, null).andReturn()
                        .getResponse().getStatus())
                .toList());

        assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
        assertThat(statuses).filteredOn(code -> code == 409).hasSize(4);
        assertThat(evaluationRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    // ---------- Consultar ----------

    @Test
    void cadaParticipanteVeLasEvaluacionesQueHizoYLasQueRecibio() throws Exception {
        UUID first = closedService();
        UUID second = closedService();
        asClient(first, 5, "Excelente").andExpect(status().isCreated());
        evaluate(technician, "ROLE_PROFESSIONAL", first, 4, "Cliente amable").andExpect(status().isCreated());
        asClient(second, 3, null).andExpect(status().isCreated());
        String ids = first + "," + second;

        mockMvc.perform(get(PATH).param("serviceRequestIds", ids).with(as(client, "ROLE_CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].rating", containsInAnyOrder(5, 4, 3)))
                .andExpect(jsonPath("$.items[*].comment", containsInAnyOrder("Excelente", "Cliente amable", null)));
        mockMvc.perform(get(PATH).param("serviceRequestIds", first.toString())
                        .with(as(technician, "ROLE_PROFESSIONAL")))
                .andExpect(jsonPath("$.items[*].direction",
                        containsInAnyOrder("CLIENT_TO_PROVIDER", "PROVIDER_TO_CLIENT")));
        // Un tercero no ve nada, aunque conozca los ids.
        mockMvc.perform(get(PATH).param("serviceRequestIds", ids).with(as(otherClient, "ROLE_CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void laConsultaPideEntreUnoYCincuentaServicios() throws Exception {
        mockMvc.perform(get(PATH).with(as(client, "ROLE_CLIENT"))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("serviceRequestIds", "").with(as(client, "ROLE_CLIENT")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("serviceRequestIds", "no-es-un-uuid").with(as(client, "ROLE_CLIENT")))
                .andExpect(status().isBadRequest());

        String fiftyOne = IntStream.range(0, 51).mapToObj(i -> UUID.randomUUID().toString())
                .collect(Collectors.joining(","));
        mockMvc.perform(get(PATH).param("serviceRequestIds", fiftyOne).with(as(client, "ROLE_CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Consulta inválida"));

        UUID repeated = UUID.randomUUID();
        String fiftyWithRepeats = IntStream.range(0, 60).mapToObj(i -> i < 49
                        ? UUID.randomUUID().toString() : repeated.toString())
                .collect(Collectors.joining(","));
        mockMvc.perform(get(PATH).param("serviceRequestIds", fiftyWithRepeats).with(as(client, "ROLE_CLIENT")))
                .andExpect(status().isOk());
    }
}
