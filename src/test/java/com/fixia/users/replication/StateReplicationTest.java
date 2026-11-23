package com.fixia.users.replication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fixia.users.client.CompanyWorkloadClient;
import com.fixia.users.client.ServiceParticipationClient;
import com.fixia.users.domain.AvailabilitySlot;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyTechnician;
import com.fixia.users.domain.CoverageZone;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.OutboxEvent;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.repository.CompanyRepository;
import com.fixia.users.repository.CompanyTechnicianRepository;
import com.fixia.users.repository.OutboxEventRepository;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replicación de estado de ms-users (GC-547): publica técnicos y empresas solo cuando cambian, y decide la participación
 * y la desvinculación con las copias que publican ms-services y ms-payments, sin llamarlos.
 */
@SpringBootTest
@ActiveProfiles("test")
class StateReplicationTest {

    @Autowired
    private StatePublisher publisher;

    @Autowired
    private ReplicatedStateHandler handler;

    @Autowired
    private StatePublicationRepository publications;

    @Autowired
    private ReplicatedStateRepository replicated;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private UserRepository users;

    @Autowired
    private TechnicianRepository technicians;

    @Autowired
    private CompanyRepository companies;

    @Autowired
    private CompanyTechnicianRepository associations;

    @Autowired
    private ServiceParticipationClient participations;

    @Autowired
    private CompanyWorkloadClient workloads;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        outbox.deleteAllInBatch();
        publications.deleteAllInBatch();
        replicated.deleteAllInBatch();
        associations.deleteAllInBatch();
        companies.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    // ---------- Utilidades ----------

    private User account(String email, String document, Role role) {
        return users.save(new User(email, "hash", role, "Laura", "Gómez", DocumentType.CC, document, "3104445566",
                "v1.0", Instant.now()));
    }

    private UUID technicianWithProfile() {
        UUID id = account("tecnica@example.com", "100", Role.PROFESSIONAL).getId();
        transactions.executeWithoutResult(status -> {
            Technician technician = technicians.save(new Technician(id, Instant.now()));
            technician.replaceCategories(Set.of(ServiceCategory.PLUMBING), Instant.now());
            technician.updateCoverageZone(new CoverageZone("Bogotá", 4.65, -74.05, 5), Instant.now());
            technician.replaceAvailability(Set.of(new AvailabilitySlot(DayOfWeek.TUESDAY, LocalTime.of(8, 0),
                    LocalTime.of(12, 0))), Instant.now());
        });
        return id;
    }

    private List<JsonNode> published(String type) {
        return outbox.findAll().stream().filter(event -> event.getEventType().equals(type))
                .map(this::payload).toList();
    }

    private JsonNode payload(OutboxEvent event) {
        try {
            return mapper.readTree(event.getPayload());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void replicate(String type, UUID key, long sequence, ObjectNode data) {
        handler.apply(new StateEvent(type, key, sequence, false, data));
    }

    // ---------- Publicación ----------

    @Test
    void publicaElEstadoDeTecnicosYEmpresasSoloCuandoCambia() {
        UUID technician = technicianWithProfile();
        User admin = account("empresa@example.com", "900", Role.COMPANY);
        Company company = companies.save(new Company(admin.getId(), "Servicios Andina", "900100200", Instant.now()));
        associations.save(new CompanyTechnician(company.getId(), technician, Instant.now()));

        assertThat(publisher.publishChanges()).isEqualTo(2);

        JsonNode state = published(TechnicianStateSource.STATE_TYPE).get(0);
        assertThat(state.at("/aggregate/id").asText()).isEqualTo(technician.toString());
        assertThat(state.at("/removed").asBoolean()).isFalse();
        assertThat(state.at("/data/active").asBoolean()).isTrue();
        assertThat(state.at("/data/displayName").asText()).isEqualTo("Laura G.");
        assertThat(state.at("/data/categories/0").asText()).isEqualTo("PLUMBING");
        assertThat(state.at("/data/coverage/radiusKm").asInt()).isEqualTo(5);
        assertThat(state.at("/data/availability/0/startTime").asText()).isEqualTo("08:00");
        assertThat(state.at("/data/companyAdminUserId").asText()).isEqualTo(admin.getId().toString());
        // Nunca datos de contacto ni el documento.
        String raw = outbox.findAll().get(0).getPayload() + outbox.findAll().get(1).getPayload();
        assertThat(raw).doesNotContain("3104445566").doesNotContain("@example.com").doesNotContain("900100200");
        OutboxEvent technicianEvent = outbox.findAll().stream()
                .filter(event -> event.getEventType().equals(TechnicianStateSource.STATE_TYPE)).findFirst()
                .orElseThrow();
        assertThat(technicianEvent.getTopic()).isEqualTo("fixia.users.technician-states");
        assertThat(technicianEvent.getMessageKey()).isEqualTo(technician.toString());
        JsonNode companyState = published(CompanyStateSource.STATE_TYPE).get(0);
        assertThat(companyState.at("/data/name").asText()).isEqualTo("Servicios Andina");
        assertThat(companyState.at("/data/active").asBoolean()).isTrue();

        assertThat(publisher.publishChanges()).isZero();

        associations.deleteAllInBatch();
        assertThat(publisher.publishChanges()).isEqualTo(1);
        JsonNode left = published(TechnicianStateSource.STATE_TYPE).stream()
                .filter(node -> node.at("/data/companyAdminUserId").isNull()).findFirst().orElseThrow();
        assertThat(left.at("/aggregate/sequence").asLong()).isGreaterThan(state.at("/aggregate/sequence").asLong());
    }

    @Test
    void unaEntidadQueDesapareceSePublicaComoEliminada() {
        technicianWithProfile();
        publisher.publishChanges();

        users.deleteAllInBatch();
        publisher.publishChanges();

        assertThat(published(TechnicianStateSource.STATE_TYPE))
                .anySatisfy(node -> assertThat(node.at("/removed").asBoolean()).isTrue());
        assertThat(publications.count()).isZero();
    }

    // ---------- Copias de ms-services y ms-payments ----------

    @Test
    void laParticipacionSaleDeLaReplica() {
        UUID request = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        ObjectNode data = mapper.createObjectNode();
        data.put("serviceRequestId", request.toString());
        data.put("status", "CLOSED");
        data.put("clientId", client.toString());
        data.put("providerId", UUID.randomUUID().toString());
        data.put("providerType", "INDEPENDENT");
        data.put("closedAt", "2026-11-22T10:00:00Z");
        replicate(ServiceParticipationClient.STATE_TYPE, request, 5, data);

        ServiceParticipationClient.Participation found = participations.find(request).orElseThrow();

        assertThat(found.isClosed()).isTrue();
        assertThat(found.clientId()).isEqualTo(client);
        assertThat(participations.find(UUID.randomUUID())).isEmpty();
        // Una versión atrasada no la deshace.
        data.put("status", "IN_PROGRESS");
        assertThat(handler.apply(new StateEvent(ServiceParticipationClient.STATE_TYPE, request, 4, false, data)))
                .isEqualTo(ReplicatedStateHandler.Outcome.STALE);
        assertThat(participations.find(request).orElseThrow().isClosed()).isTrue();
    }

    @Test
    void elTrabajoYElDineroRetenidoSalenDeLaReplica() {
        UUID technician = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        UUID done = UUID.randomUUID();
        ObjectNode workload = mapper.createObjectNode();
        workload.put("technicianUserId", technician.toString());
        ObjectNode perCompany = workload.putArray("companies").addObject();
        perCompany.put("companyAdminUserId", company.toString());
        perCompany.putArray("pendingServiceRequestIds").add(pending.toString());
        perCompany.putArray("serviceRequestIds").add(pending.toString()).add(done.toString());
        replicate(CompanyWorkloadClient.WORKLOAD_TYPE, technician, 1, workload);
        ObjectNode held = mapper.createObjectNode();
        held.put("serviceRequestId", done.toString());
        held.put("providerId", company.toString());
        held.put("held", true);
        replicate(CompanyWorkloadClient.HELD_TYPE, done, 1, held);

        CompanyWorkloadClient.Workload found = workloads.workload(company, technician);

        assertThat(found.pendingServiceRequestIds()).containsExactly(pending);
        assertThat(workloads.held(company, found.serviceRequestIds())).containsExactly(done);
        assertThat(workloads.workload(UUID.randomUUID(), technician).serviceRequestIds()).isEmpty();
        assertThat(workloads.held(UUID.randomUUID(), found.serviceRequestIds())).isEmpty();
    }
}
