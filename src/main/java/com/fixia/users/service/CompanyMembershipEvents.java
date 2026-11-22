package com.fixia.users.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyMembershipEvent;
import com.fixia.users.domain.OutboxEvent;
import com.fixia.users.domain.Role;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Arma {@code empresa.tecnico_vinculado} y {@code empresa.tecnico_desvinculado} (versión 1) para el Outbox (GC-524,
 * contrato de empresas §6.3). Los esquemas están en {@code docs/events/schemas}. Sin correo, nombre ni documento.
 *
 * <p>El agregado es la asociación ({@code company_technicians.id}): la vinculación es su secuencia 1 y la
 * desvinculación su secuencia 2. La clave del mensaje es el técnico, para que su historia llegue en orden a la misma
 * partición. El {@code correlationId} también es la asociación: enlaza la vinculación con su desvinculación.
 */
@Component
public class CompanyMembershipEvents {

    static final String TOPIC = "fixia.users.companies";
    static final String TECHNICIAN_JOINED = "empresa.tecnico_vinculado";
    static final String TECHNICIAN_LEFT = "empresa.tecnico_desvinculado";
    static final short V1 = 1;
    static final String AGGREGATE_TYPE = "CompanyMembership";
    static final long JOINED_SEQUENCE = 1;
    static final long LEFT_SEQUENCE = 2;

    record Aggregate(String type, UUID id, long sequence) {
    }

    record Actor(UUID userId, String role) {
    }

    record TechnicianJoined(UUID companyId, UUID companyAdminUserId, UUID technicianUserId, UUID invitationId,
                            Instant joinedAt) {
    }

    record TechnicianLeft(UUID companyId, UUID companyAdminUserId, UUID technicianUserId, Instant leftAt) {
    }

    record Envelope(UUID eventId, String eventType, int schemaVersion, Instant occurredAt, String producer,
                    Aggregate aggregate, UUID correlationId, Actor actor, Object data) {
    }

    private final ObjectMapper objectMapper;

    public CompanyMembershipEvents(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEvent joined(CompanyMembershipEvent event, Company company) {
        return build(event, TECHNICIAN_JOINED, JOINED_SEQUENCE, new TechnicianJoined(company.getId(),
                company.getAdminUserId(), event.getTechnicianUserId(), event.getInvitationId(), event.getOccurredAt()));
    }

    public OutboxEvent left(CompanyMembershipEvent event, Company company) {
        return build(event, TECHNICIAN_LEFT, LEFT_SEQUENCE, new TechnicianLeft(company.getId(),
                company.getAdminUserId(), event.getTechnicianUserId(), event.getOccurredAt()));
    }

    /** En ambos casos actúa el técnico: acepta la invitación o se desvincula (la empresa no desvincula, P1). */
    private OutboxEvent build(CompanyMembershipEvent event, String type, long sequence, Object data) {
        UUID eventId = UUID.randomUUID();
        UUID membershipId = event.getMembershipId();
        Envelope envelope = new Envelope(eventId, type, V1, event.getOccurredAt(), EvaluationEvents.PRODUCER,
                new Aggregate(AGGREGATE_TYPE, membershipId, sequence), membershipId,
                new Actor(event.getTechnicianUserId(), Role.PROFESSIONAL.name()), data);
        return OutboxEvent.pending(eventId, type, V1, AGGREGATE_TYPE, membershipId, sequence, TOPIC,
                event.getTechnicianUserId().toString(), serialize(envelope), event.getOccurredAt());
    }

    private String serialize(Envelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            // No debería pasar con estos registros; si pasa, el cambio se revierte con el evento.
            throw new IllegalStateException("No se pudo serializar el evento " + envelope.eventType(), e);
        }
    }
}
