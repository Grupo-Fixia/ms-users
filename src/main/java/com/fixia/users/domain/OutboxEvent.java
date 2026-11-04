package com.fixia.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Un hecho de negocio de ms-users pendiente de publicar en Kafka (GC-444, Outbox transaccional). Misma tabla y mismas
 * reglas que el Outbox de ms-services (GC-433): se escribe en la misma transacción que el cambio y el relay (GC-434)
 * lo publica después del commit y actualiza solo las columnas de envío. Aquí solo se crea; publicarlo no es tarea de
 * este servicio.
 *
 * <p>Implementa {@link Persistable} porque el id lo genera la aplicación: así guardar es siempre un insert.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent implements Persistable<UUID> {

    public static final int MAX_PAYLOAD_LENGTH = 16000;

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private Short schemaVersion;

    @Column(name = "aggregate_type", nullable = false, length = 40, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_sequence", nullable = false, updatable = false)
    private Long aggregateSequence;

    @Column(name = "topic", nullable = false, length = 120, updatable = false)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 60, updatable = false)
    private String messageKey;

    @Column(name = "payload", nullable = false, length = MAX_PAYLOAD_LENGTH, updatable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Transient
    private boolean isNew;

    protected OutboxEvent() {
    }

    private OutboxEvent(UUID eventId, String eventType, short schemaVersion, String aggregateType, UUID aggregateId,
                        long aggregateSequence, String topic, String messageKey, String payload, Instant occurredAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.aggregateSequence = aggregateSequence;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = occurredAt;
        this.isNew = true;
    }

    public static OutboxEvent pending(UUID eventId, String eventType, short schemaVersion, String aggregateType,
                                      UUID aggregateId, long aggregateSequence, String topic, String messageKey,
                                      String payload, Instant occurredAt) {
        require(eventId != null && aggregateId != null, "El evento y su agregado son obligatorios");
        require(eventType != null && !eventType.isBlank(), "El tipo del evento es obligatorio");
        require(schemaVersion >= 1 && aggregateSequence >= 1, "La versión y la secuencia deben ser positivas");
        require(aggregateType != null && !aggregateType.isBlank(), "El tipo del agregado es obligatorio");
        require(topic != null && !topic.isBlank() && messageKey != null && !messageKey.isBlank(),
                "El tópico y la clave son obligatorios");
        require(payload != null && !payload.isBlank() && payload.length() <= MAX_PAYLOAD_LENGTH,
                "El contenido del evento es obligatorio y admite hasta " + MAX_PAYLOAD_LENGTH + " caracteres");
        require(occurredAt != null, "El instante del hecho es obligatorio");
        return new OutboxEvent(eventId, eventType, schemaVersion, aggregateType, aggregateId, aggregateSequence,
                topic, messageKey, payload, occurredAt);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public short getSchemaVersion() {
        return schemaVersion;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public long getAggregateSequence() {
        return aggregateSequence;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }
}
