package com.fixia.users.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.client.CompanyWorkloadClient;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyInvitation;
import com.fixia.users.domain.CompanyMembershipEvent;
import com.fixia.users.domain.CompanyTechnician;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.InvitationCancelReason;
import com.fixia.users.domain.InvitationStatus;
import com.fixia.users.domain.OutboxEvent;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.exception.ServicesUnavailableException;
import com.fixia.users.repository.CompanyInvitationRepository;
import com.fixia.users.repository.CompanyMembershipEventRepository;
import com.fixia.users.repository.CompanyRepository;
import com.fixia.users.repository.CompanyTechnicianRepository;
import com.fixia.users.repository.OutboxEventRepository;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El técnico ante las invitaciones empresariales (GC-524, contrato de empresas §5): recibirlas, aceptarlas,
 * rechazarlas, ver su empresa y desvincularse. Las cuentas inician sesión por la API; ms-services y ms-payments se
 * simulan con {@link CompanyWorkloadClient}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TechnicianMembershipControllerTest {

    private static final String PASSWORD = "Segura#2026";
    private static final String ME = "/api/users/technicians/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyTechnicianRepository associationRepository;

    @Autowired
    private CompanyInvitationRepository invitationRepository;

    @Autowired
    private CompanyMembershipEventRepository historyRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private CompanyWorkloadClient workloadClient;

    private Company companyA;
    private Company companyB;
    private UUID carlos;
    private UUID diana;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAllInBatch();
        // El borrado en cascada se lleva técnicos, empresas, asociaciones, invitaciones e historial.
        userRepository.deleteAllInBatch();
        companyA = company("empresa-a@fixia.test", "9100000001", "Servicios Andina");
        companyB = company("empresa-b@fixia.test", "9100000002", "Mantenimientos del Valle");
        carlos = technician("carlos@fixia.test", "9200000001", "Carlos", "Giraldo");
        diana = technician("diana@fixia.test", "9200000002", "Diana", "Ruiz");
        when(workloadClient.workload(any(), any()))
                .thenReturn(new CompanyWorkloadClient.Workload(List.of(), List.of()));
        when(workloadClient.held(any(), anyList())).thenReturn(List.of());
    }

    // ---------- Utilidades ----------

    private User account(String email, String document, Role role, String firstName, String lastName) {
        return userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), role, firstName, lastName,
                DocumentType.CC, document, "3104445566", "v1.0", Instant.now()));
    }

    private Company company(String email, String document, String name) {
        User admin = account(email, document, Role.COMPANY, "Laura", "Gómez");
        return companyRepository.save(new Company(admin.getId(), name, "900100200", Instant.now()));
    }

    private UUID technician(String email, String document, String firstName, String lastName) {
        UUID userId = account(email, document, Role.PROFESSIONAL, firstName, lastName).getId();
        technicianRepository.save(new Technician(userId, Instant.now()));
        return userId;
    }

    /** Invitación pendiente creada hace {@code age} y vigente por 7 días desde entonces. */
    private CompanyInvitation invitation(Company company, UUID technician, Duration age) {
        Instant created = Instant.now().minus(age);
        return invitationRepository.saveAndFlush(new CompanyInvitation(company.getId(), technician, created,
                created.plus(Duration.ofDays(7))));
    }

    private CompanyInvitation invitation(Company company, UUID technician) {
        return invitation(company, technician, Duration.ofMinutes(1));
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, token);
    }

    private MockHttpServletRequestBuilder accept(UUID invitationId) {
        return post(ME + "/invitations/" + invitationId + "/accept");
    }

    private MockHttpServletRequestBuilder reject(UUID invitationId) {
        return post(ME + "/invitations/" + invitationId + "/reject");
    }

    private CompanyTechnician joined(Company company, UUID technician) {
        return associationRepository.saveAndFlush(new CompanyTechnician(company.getId(), technician, Instant.now()));
    }

    private List<OutboxEvent> outbox(String type) {
        return outboxRepository.findAll().stream().filter(event -> event.getEventType().equals(type)).toList();
    }

    // ---------- Invitaciones recibidas ----------

    @Test
    void elTecnicoSoloVeSusInvitacionesAbiertasDeEmpresasActivas() throws Exception {
        CompanyInvitation closed = invitation(companyA, carlos, Duration.ofDays(8));
        closed.closeAsExpired(Instant.now());
        invitationRepository.saveAndFlush(closed);
        CompanyInvitation older = invitation(companyA, carlos, Duration.ofHours(2));
        CompanyInvitation newer = invitation(companyB, carlos, Duration.ofHours(1));
        invitation(companyA, diana);

        mockMvc.perform(as(get(ME + "/invitations"), login("carlos@fixia.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations", hasSize(2)))
                .andExpect(jsonPath("$.invitations[*].id",
                        contains(newer.getId().toString(), older.getId().toString())))
                .andExpect(jsonPath("$.invitations[0].company.name").value("Mantenimientos del Valle"))
                .andExpect(jsonPath("$.invitations[0].status").value("PENDING"))
                .andExpect(jsonPath("$.invitations[0].expiresAt").exists())
                .andExpect(jsonPath("$.invitations[0].company.nit").doesNotExist());

        User admin = userRepository.findById(companyB.getAdminUserId()).orElseThrow();
        admin.disable();
        userRepository.save(admin);
        mockMvc.perform(as(get(ME + "/invitations"), login("carlos@fixia.test")))
                .andExpect(jsonPath("$.invitations[*].id", contains(older.getId().toString())));
    }

    @Test
    void unaInvitacionVencidaNoSeMuestraNiSeAcepta() throws Exception {
        CompanyInvitation expired = invitation(companyA, carlos, Duration.ofDays(8));
        String token = login("carlos@fixia.test");

        mockMvc.perform(as(get(ME + "/invitations"), token)).andExpect(jsonPath("$.invitations", hasSize(0)));
        mockMvc.perform(as(accept(expired.getId()), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasons", contains("INVITATION_NOT_PENDING")));
        assertThat(associationRepository.existsByTechnicianUserId(carlos)).isFalse();
    }

    // ---------- Aceptar ----------

    @Test
    void aceptarVinculaAlTecnicoCancelaSusOtrasInvitacionesYPublicaElEvento() throws Exception {
        CompanyInvitation fromA = invitation(companyA, carlos);
        CompanyInvitation fromB = invitation(companyB, carlos);

        mockMvc.perform(as(accept(fromA.getId()), login("carlos@fixia.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company.name").value("Servicios Andina"))
                .andExpect(jsonPath("$.joinedAt").exists());

        CompanyTechnician membership = associationRepository.findByTechnicianUserId(carlos).orElseThrow();
        assertThat(membership.getCompanyId()).isEqualTo(companyA.getId());
        assertThat(invitationRepository.findById(fromA.getId()).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.ACCEPTED);
        CompanyInvitation other = invitationRepository.findById(fromB.getId()).orElseThrow();
        assertThat(other.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
        assertThat(other.getCancelReason()).isEqualTo(InvitationCancelReason.JOINED_OTHER_COMPANY);

        List<CompanyMembershipEvent> history = historyRepository.findByTechnicianUserIdOrderByOccurredAtAsc(carlos);
        assertThat(history).singleElement().satisfies(event -> {
            assertThat(event.getType()).isEqualTo(CompanyMembershipEvent.Type.JOINED);
            assertThat(event.getInvitationId()).isEqualTo(fromA.getId());
            assertThat(event.getMembershipId()).isEqualTo(membership.getId());
        });

        OutboxEvent event = outbox("empresa.tecnico_vinculado").get(0);
        assertThat(event.getTopic()).isEqualTo("fixia.users.companies");
        assertThat(event.getMessageKey()).isEqualTo(carlos.toString());
        assertThat(event.getAggregateId()).isEqualTo(membership.getId());
        assertThat(event.getAggregateSequence()).isEqualTo(1);
        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("producer").asText()).isEqualTo("ms-users");
        assertThat(payload.at("/aggregate/type").asText()).isEqualTo("CompanyMembership");
        assertThat(payload.at("/actor/userId").asText()).isEqualTo(carlos.toString());
        assertThat(payload.at("/actor/role").asText()).isEqualTo("PROFESSIONAL");
        assertThat(payload.at("/data/companyId").asText()).isEqualTo(companyA.getId().toString());
        assertThat(payload.at("/data/companyAdminUserId").asText()).isEqualTo(companyA.getAdminUserId().toString());
        assertThat(payload.at("/data/technicianUserId").asText()).isEqualTo(carlos.toString());
        assertThat(payload.at("/data/invitationId").asText()).isEqualTo(fromA.getId().toString());
        assertThat(payload.get("data").has("joinedAt")).isTrue();
        assertThat(event.getPayload()).doesNotContain("carlos@fixia.test", "Giraldo", "9200000001");

        // La empresa ya lo ve entre sus técnicos y su invitación aparece aceptada.
        String companyToken = login("empresa-a@fixia.test");
        mockMvc.perform(as(get("/api/users/companies/me/technicians"), companyToken))
                .andExpect(jsonPath("$.technicians", hasSize(1)));
        mockMvc.perform(as(get("/api/users/companies/me/invitations"), companyToken))
                .andExpect(jsonPath("$.invitations[0].status").value("ACCEPTED"));
        mockMvc.perform(as(get("/api/users/companies/me/invitations"), login("empresa-b@fixia.test")))
                .andExpect(jsonPath("$.invitations[0].status").value("CANCELLED"));
    }

    @Test
    void unTecnicoQueYaTieneEmpresaNoAceptaOtraNiPuedeSerInvitado() throws Exception {
        joined(companyB, carlos);
        CompanyInvitation fromA = invitation(companyA, carlos);

        mockMvc.perform(as(accept(fromA.getId()), login("carlos@fixia.test")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasons", contains("ALREADY_IN_COMPANY")));
        assertThat(invitationRepository.findById(fromA.getId()).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.PENDING);

        mockMvc.perform(as(post("/api/users/companies/me/invitations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianEmail\":\"carlos@fixia.test\"}"), login("empresa-a@fixia.test")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unaInvitacionAjenaOInexistenteEs404YUnaYaRespondidaEs409() throws Exception {
        CompanyInvitation forDiana = invitation(companyA, diana);
        CompanyInvitation cancelled = invitation(companyB, carlos);
        cancelled.cancelByCompany(Instant.now());
        invitationRepository.save(cancelled);
        String token = login("carlos@fixia.test");

        mockMvc.perform(as(accept(forDiana.getId()), token)).andExpect(status().isNotFound());
        mockMvc.perform(as(reject(forDiana.getId()), token)).andExpect(status().isNotFound());
        mockMvc.perform(as(accept(UUID.randomUUID()), token)).andExpect(status().isNotFound());
        mockMvc.perform(as(accept(cancelled.getId()), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasons", contains("INVITATION_NOT_PENDING")));
        assertThat(invitationRepository.findById(forDiana.getId()).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void laEmpresaNoCancelaUnaInvitacionYaAceptada() throws Exception {
        CompanyInvitation fromA = invitation(companyA, carlos);
        mockMvc.perform(as(accept(fromA.getId()), login("carlos@fixia.test"))).andExpect(status().isOk());

        mockMvc.perform(as(delete("/api/users/companies/me/invitations/" + fromA.getId()),
                        login("empresa-a@fixia.test")))
                .andExpect(status().isConflict());
    }

    @Test
    void siLaEmpresaYaNoEstaActivaNoSePuedeAceptar() throws Exception {
        CompanyInvitation fromA = invitation(companyA, carlos);
        User admin = userRepository.findById(companyA.getAdminUserId()).orElseThrow();
        admin.disable();
        userRepository.save(admin);

        mockMvc.perform(as(accept(fromA.getId()), login("carlos@fixia.test")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasons", contains("COMPANY_UNAVAILABLE")));
        assertThat(associationRepository.existsByTechnicianUserId(carlos)).isFalse();
    }

    @Test
    void dosAceptacionesSimultaneasDejanAlTecnicoEnUnaSolaEmpresa() throws Exception {
        CompanyInvitation fromA = invitation(companyA, carlos);
        CompanyInvitation fromB = invitation(companyB, carlos);
        String token = login("carlos@fixia.test");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(fromA.getId(), fromB.getId()).stream()
                    .<Callable<Integer>>map(id -> () -> {
                        start.await();
                        return mockMvc.perform(as(accept(id), token)).andReturn().getResponse().getStatus();
                    })
                    .toList();
            List<Future<Integer>> futures = calls.stream().map(pool::submit).toList();
            start.countDown();
            List<Integer> statuses = futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(associationRepository.findAll().stream()
                .filter(membership -> membership.getTechnicianUserId().equals(carlos))).hasSize(1);
        assertThat(historyRepository.findByTechnicianUserIdOrderByOccurredAtAsc(carlos)).hasSize(1);
        assertThat(outbox("empresa.tecnico_vinculado")).hasSize(1);
    }

    // ---------- Rechazar ----------

    @Test
    void rechazarCierraLaInvitacionSinVincular() throws Exception {
        CompanyInvitation fromA = invitation(companyA, carlos);
        String token = login("carlos@fixia.test");

        mockMvc.perform(as(reject(fromA.getId()), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.company.name").value("Servicios Andina"))
                .andExpect(jsonPath("$.respondedAt").exists());

        mockMvc.perform(as(accept(fromA.getId()), token)).andExpect(status().isConflict());
        mockMvc.perform(as(reject(fromA.getId()), token)).andExpect(status().isConflict());
        assertThat(associationRepository.existsByTechnicianUserId(carlos)).isFalse();
        assertThat(outbox("empresa.tecnico_vinculado")).isEmpty();
        mockMvc.perform(as(get("/api/users/companies/me/invitations"), login("empresa-a@fixia.test")))
                .andExpect(jsonPath("$.invitations[0].status").value("REJECTED"));
    }

    // ---------- Mi empresa y desvinculación ----------

    @Test
    void unTecnicoIndependienteNoTieneEmpresa() throws Exception {
        String token = login("carlos@fixia.test");
        mockMvc.perform(as(get(ME + "/company"), token)).andExpect(status().isNotFound());
        mockMvc.perform(as(delete(ME + "/company"), token)).andExpect(status().isNotFound());
        verify(workloadClient, never()).workload(any(), any());
    }

    @Test
    void sinTrabajoNiSaldoElTecnicoSeDesvinculaYQuedaElHistorial() throws Exception {
        CompanyInvitation fromA = invitation(companyA, carlos);
        String token = login("carlos@fixia.test");
        mockMvc.perform(as(accept(fromA.getId()), token)).andExpect(status().isOk());
        UUID membershipId = associationRepository.findByTechnicianUserId(carlos).orElseThrow().getId();
        UUID closed = UUID.randomUUID();
        when(workloadClient.workload(companyA.getAdminUserId(), carlos))
                .thenReturn(new CompanyWorkloadClient.Workload(List.of(), List.of(closed)));

        mockMvc.perform(as(get(ME + "/company"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company.name").value("Servicios Andina"));
        mockMvc.perform(as(delete(ME + "/company"), token)).andExpect(status().isNoContent());

        verify(workloadClient).held(companyA.getAdminUserId(), List.of(closed));
        assertThat(associationRepository.existsByTechnicianUserId(carlos)).isFalse();
        assertThat(historyRepository.findByTechnicianUserIdOrderByOccurredAtAsc(carlos))
                .extracting(CompanyMembershipEvent::getType)
                .containsExactly(CompanyMembershipEvent.Type.JOINED, CompanyMembershipEvent.Type.LEFT);
        OutboxEvent left = outbox("empresa.tecnico_desvinculado").get(0);
        assertThat(left.getAggregateId()).isEqualTo(membershipId);
        assertThat(left.getAggregateSequence()).isEqualTo(2);
        JsonNode payload = objectMapper.readTree(left.getPayload());
        assertThat(payload.at("/data/companyAdminUserId").asText()).isEqualTo(companyA.getAdminUserId().toString());
        assertThat(payload.get("data").has("leftAt")).isTrue();
        assertThat(payload.get("data").has("invitationId")).isFalse();

        mockMvc.perform(as(get(ME + "/company"), token)).andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/users/companies/me/technicians"), login("empresa-a@fixia.test")))
                .andExpect(jsonPath("$.technicians", hasSize(0)));
        // Vuelve a ser independiente: otra empresa lo puede invitar.
        mockMvc.perform(as(post("/api/users/companies/me/invitations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianEmail\":\"carlos@fixia.test\"}"), login("empresa-b@fixia.test")))
                .andExpect(status().isCreated());
    }

    @Test
    void conTrabajoPendienteOSaldoRetenidoNoSeDesvincula() throws Exception {
        joined(companyA, carlos);
        String token = login("carlos@fixia.test");
        UUID open = UUID.randomUUID();
        UUID closedButHeld = UUID.randomUUID();
        when(workloadClient.workload(companyA.getAdminUserId(), carlos))
                .thenReturn(new CompanyWorkloadClient.Workload(List.of(open), List.of(open, closedButHeld)));
        when(workloadClient.held(eq(companyA.getAdminUserId()), anyList())).thenReturn(List.of(closedButHeld));

        mockMvc.perform(as(delete(ME + "/company"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasons", contains("PENDING_WORK", "HELD_BALANCE")))
                .andExpect(jsonPath("$.serviceRequestIds",
                        containsInAnyOrder(open.toString(), closedButHeld.toString())));

        when(workloadClient.workload(companyA.getAdminUserId(), carlos))
                .thenReturn(new CompanyWorkloadClient.Workload(List.of(), List.of(closedButHeld)));
        mockMvc.perform(as(delete(ME + "/company"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasons", contains("HELD_BALANCE")));

        assertThat(associationRepository.existsByTechnicianUserId(carlos)).isTrue();
        assertThat(historyRepository.findByTechnicianUserIdOrderByOccurredAtAsc(carlos)).isEmpty();
        assertThat(outbox("empresa.tecnico_desvinculado")).isEmpty();
    }

    @Test
    void siMsServicesOMsPaymentsNoRespondenNoSeDesvincula() throws Exception {
        joined(companyA, carlos);
        String token = login("carlos@fixia.test");

        when(workloadClient.workload(any(), any())).thenThrow(new ServicesUnavailableException("caído", null));
        mockMvc.perform(as(delete(ME + "/company"), token)).andExpect(status().isServiceUnavailable());

        doReturn(new CompanyWorkloadClient.Workload(List.of(), List.of(UUID.randomUUID())))
                .when(workloadClient).workload(any(), any());
        doThrow(new ServicesUnavailableException("caído", null)).when(workloadClient).held(any(), anyList());
        mockMvc.perform(as(delete(ME + "/company"), token)).andExpect(status().isServiceUnavailable());

        assertThat(associationRepository.existsByTechnicianUserId(carlos)).isTrue();
    }

    // ---------- Acceso ----------

    @Test
    void soloElTecnicoUsaEstasRutas() throws Exception {
        CompanyInvitation fromA = invitation(companyA, carlos);
        String companyToken = login("empresa-a@fixia.test");

        mockMvc.perform(get(ME + "/invitations")).andExpect(status().isUnauthorized());
        mockMvc.perform(as(get(ME + "/invitations"), companyToken)).andExpect(status().isForbidden());
        mockMvc.perform(as(accept(fromA.getId()), companyToken)).andExpect(status().isForbidden());
        mockMvc.perform(as(delete(ME + "/company"), companyToken)).andExpect(status().isForbidden());
        assertThat(invitationRepository.findById(fromA.getId()).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.PENDING);
    }
}
