package com.fixia.users.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.EvaluationDirection;
import com.fixia.users.domain.EvaluationSource;
import com.fixia.users.domain.OutboxEvent;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.ServiceEvaluation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Arma el evento {@code evaluacion.registrada} (versión 1) para el Outbox (GC-444). La forma la fija el contrato de
 * GC-443: {@code docs/events/schemas/evaluacion.registrada.v1.schema.json}. Nunca lleva el comentario.
 */
@Component
public class EvaluationEvents {

    static final String TOPIC = "fixia.users.evaluations";
    static final String EVALUATION_REGISTERED = "evaluacion.registrada";
    static final short V1 = 1;
    static final String PRODUCER = "ms-users";
    static final String AGGREGATE_TYPE = "Evaluation";

    record Aggregate(String type, UUID id, long sequence) {
    }

    record Actor(UUID userId, String role) {
    }

    record EvaluationRegistered(UUID evaluationId, UUID serviceRequestId, EvaluationDirection direction,
                                UUID evaluatorId, UUID evaluatedId, int rating, EvaluationSource source,
                                Instant serviceClosedAt) {
    }

    /** Mismo sobre que los eventos de ms-services; {@code correlationId} es el id de la solicitud. */
    record Envelope(UUID eventId, String eventType, int schemaVersion, Instant occurredAt, String producer,
                    Aggregate aggregate, UUID correlationId, Actor actor, Object data) {
    }

    private final ObjectMapper objectMapper;

    public EvaluationEvents(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Una evaluación es un hecho único: su agregado es ella misma y su secuencia siempre 1. La clave del mensaje es el
     * evaluado, para que todas sus evaluaciones lleguen en orden a la misma partición (reputación, GC-446).
     */
    public OutboxEvent registered(ServiceEvaluation evaluation, Role evaluatorRole) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = evaluation.getCreatedAt();
        EvaluationRegistered data = new EvaluationRegistered(evaluation.getId(), evaluation.getServiceRequestId(),
                evaluation.getDirection(), evaluation.getEvaluatorId(), evaluation.getEvaluatedId(),
                evaluation.getRating(), evaluation.getSource(), evaluation.getServiceClosedAt());
        Envelope envelope = new Envelope(eventId, EVALUATION_REGISTERED, V1, occurredAt, PRODUCER,
                new Aggregate(AGGREGATE_TYPE, evaluation.getId(), 1), evaluation.getServiceRequestId(),
                new Actor(evaluation.getEvaluatorId(), evaluatorRole.name()), data);
        return OutboxEvent.pending(eventId, EVALUATION_REGISTERED, V1, AGGREGATE_TYPE, evaluation.getId(), 1, TOPIC,
                evaluation.getEvaluatedId().toString(), serialize(envelope), occurredAt);
    }

    private String serialize(Envelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            // No debería pasar con estos registros; si pasa, la evaluación se revierte con el evento.
            throw new IllegalStateException("No se pudo serializar el evento " + envelope.eventType(), e);
        }
    }
}
