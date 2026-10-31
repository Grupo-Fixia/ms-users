package com.fixia.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evaluación de un participante de un servicio cerrado a su contraparte (GC-444, RN-012). ms-users es su custodio; el
 * servicio vive en ms-services y solo se guarda su id. Es inmutable: no hay métodos para cambiarla (DD CD-08: toda
 * corrección es un hecho nuevo).
 */
@Entity
@Table(name = "service_evaluations")
public class ServiceEvaluation {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MAX_COMMENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_request_id", nullable = false, updatable = false)
    private UUID serviceRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 30, updatable = false)
    private EvaluationDirection direction;

    @Column(name = "evaluator_id", nullable = false, updatable = false)
    private UUID evaluatorId;

    @Column(name = "evaluated_id", nullable = false, updatable = false)
    private UUID evaluatedId;

    @Column(name = "rating", nullable = false, updatable = false)
    private Short rating;

    @Column(name = "comment", length = MAX_COMMENT_LENGTH, updatable = false)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30, updatable = false)
    private EvaluationSource source;

    @Column(name = "service_closed_at", nullable = false, updatable = false)
    private Instant serviceClosedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ServiceEvaluation() {
    }

    private ServiceEvaluation(UUID serviceRequestId, EvaluationDirection direction, UUID evaluatorId,
                              UUID evaluatedId, short rating, String comment, Instant serviceClosedAt, Instant now) {
        this.serviceRequestId = serviceRequestId;
        this.direction = direction;
        this.evaluatorId = evaluatorId;
        this.evaluatedId = evaluatedId;
        this.rating = rating;
        this.comment = comment;
        this.source = EvaluationSource.CONFIRMED_CLOSURE;
        this.serviceClosedAt = serviceClosedAt;
        this.createdAt = now;
    }

    /**
     * @param comment se guarda sin espacios a los lados; vacío o solo espacios se guarda como {@code null}
     */
    public static ServiceEvaluation register(UUID serviceRequestId, EvaluationDirection direction, UUID evaluatorId,
                                             UUID evaluatedId, int rating, String comment, Instant serviceClosedAt,
                                             Instant now) {
        require(serviceRequestId != null, "El servicio es obligatorio");
        require(direction != null, "La dirección es obligatoria");
        require(evaluatorId != null && evaluatedId != null, "El evaluador y el evaluado son obligatorios");
        require(!evaluatorId.equals(evaluatedId), "Nadie se evalúa a sí mismo");
        require(rating >= MIN_RATING && rating <= MAX_RATING, "La calificación va de 1 a 5");
        String normalized = normalizeComment(comment);
        require(normalized == null || normalized.length() <= MAX_COMMENT_LENGTH,
                "El comentario admite hasta " + MAX_COMMENT_LENGTH + " caracteres");
        require(serviceClosedAt != null && now != null, "Faltan las fechas del cierre y de la evaluación");
        return new ServiceEvaluation(serviceRequestId, direction, evaluatorId, evaluatedId, (short) rating,
                normalized, serviceClosedAt, now);
    }

    public static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.strip();
    }

    /** Si un nuevo envío es la misma evaluación (mismo autor, nota y comentario): un reintento, no un cambio. */
    public boolean isSameAs(UUID evaluatorId, int rating, String comment) {
        return this.evaluatorId.equals(evaluatorId) && this.rating == rating
                && Objects.equals(this.comment, normalizeComment(comment));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getServiceRequestId() {
        return serviceRequestId;
    }

    public EvaluationDirection getDirection() {
        return direction;
    }

    public UUID getEvaluatorId() {
        return evaluatorId;
    }

    public UUID getEvaluatedId() {
        return evaluatedId;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public EvaluationSource getSource() {
        return source;
    }

    public Instant getServiceClosedAt() {
        return serviceClosedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
