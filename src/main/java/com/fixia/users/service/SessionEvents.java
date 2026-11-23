package com.fixia.users.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.OutboxEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Arma {@code sesion.revocada} (versión 1) para el Outbox (GC-547). Los demás servicios guardan el {@code jti} en una
 * lista local y deciden con ella, sin llamar a ms-users; un token revocado sirve allí lo que tarda el evento en llegar
 * (retraso del Outbox más Kafka). El esquema está en {@code docs/events/schemas}.
 *
 * <p>Va a {@code fixia.users.sessions} con clave = {@code jti}. El agregado es la sesión ({@code Session}, {@code id} =
 * {@code jti}, secuencia 1: un token se revoca una sola vez). Sin correo, nombre ni el token: solo su identificador y
 * hasta cuándo hay que recordarlo.
 */
@Component
public class SessionEvents {

    static final String TOPIC = "fixia.users.sessions";
    static final String SESSION_REVOKED = "sesion.revocada";
    static final short V1 = 1;
    static final String AGGREGATE_TYPE = "Session";
    static final long SEQUENCE = 1;

    record Aggregate(String type, UUID id, long sequence) {
    }

    record Actor(UUID userId, String role) {
    }

    record SessionRevoked(UUID jti, Instant expiresAt, Instant revokedAt) {
    }

    record Envelope(UUID eventId, String eventType, int schemaVersion, Instant occurredAt, String producer,
                    Aggregate aggregate, UUID correlationId, Actor actor, Object data) {
    }

    private final ObjectMapper objectMapper;

    public SessionEvents(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param jti       identificador del access token revocado
     * @param userId    dueño de la sesión, que la cerró
     * @param role      su rol sin el prefijo {@code ROLE_}
     * @param expiresAt vencimiento del token: después de ese momento ya no hay que recordarlo
     */
    public OutboxEvent revoked(UUID jti, UUID userId, String role, Instant expiresAt, Instant revokedAt) {
        UUID eventId = UUID.randomUUID();
        Envelope envelope = new Envelope(eventId, SESSION_REVOKED, V1, revokedAt, EvaluationEvents.PRODUCER,
                new Aggregate(AGGREGATE_TYPE, jti, SEQUENCE), jti, new Actor(userId, role),
                new SessionRevoked(jti, expiresAt, revokedAt));
        return OutboxEvent.pending(eventId, SESSION_REVOKED, V1, AGGREGATE_TYPE, jti, SEQUENCE, TOPIC, jti.toString(),
                serialize(envelope), revokedAt);
    }

    private String serialize(Envelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar sesion.revocada", e);
        }
    }
}
