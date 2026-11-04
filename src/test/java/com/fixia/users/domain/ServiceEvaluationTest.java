package com.fixia.users.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceEvaluationTest {

    private static final UUID SERVICE = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID TECHNICIAN = UUID.randomUUID();
    private static final Instant CLOSED = Instant.parse("2026-10-29T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");

    private static ServiceEvaluation evaluation(int rating, String comment) {
        return ServiceEvaluation.register(SERVICE, EvaluationDirection.CLIENT_TO_PROVIDER, CLIENT, TECHNICIAN, rating,
                comment, CLOSED, NOW);
    }

    @Test
    void guardaLaEvaluacionConElComentarioLimpio() {
        ServiceEvaluation evaluation = evaluation(5, "  Muy puntual  ");

        assertThat(evaluation.getRating()).isEqualTo(5);
        assertThat(evaluation.getComment()).isEqualTo("Muy puntual");
        assertThat(evaluation.getSource()).isEqualTo(EvaluationSource.CONFIRMED_CLOSURE);
        assertThat(evaluation.getServiceClosedAt()).isEqualTo(CLOSED);
        assertThat(evaluation.getCreatedAt()).isEqualTo(NOW);
        assertThat(evaluation(4, "   ").getComment()).isNull();
        assertThat(evaluation(4, null).getComment()).isNull();
    }

    @Test
    void reconoceUnReintentoIdentico() {
        ServiceEvaluation evaluation = evaluation(5, "Muy puntual");

        assertThat(evaluation.isSameAs(CLIENT, 5, " Muy puntual ")).isTrue();
        assertThat(evaluation.isSameAs(CLIENT, 4, "Muy puntual")).isFalse();
        assertThat(evaluation.isSameAs(CLIENT, 5, "Otro")).isFalse();
        assertThat(evaluation.isSameAs(CLIENT, 5, null)).isFalse();
        assertThat(evaluation.isSameAs(TECHNICIAN, 5, "Muy puntual")).isFalse();
        assertThat(evaluation(3, null).isSameAs(CLIENT, 3, "  ")).isTrue();
    }

    @Test
    void rechazaDatosQueRompenLasReglas() {
        assertThatThrownBy(() -> evaluation(0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluation(6, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluation(5, "x".repeat(501))).isInstanceOf(IllegalArgumentException.class);
        assertThat(evaluation(5, " " + "x".repeat(500) + " ").getComment()).hasSize(500);
        assertThatThrownBy(() -> ServiceEvaluation.register(SERVICE, EvaluationDirection.CLIENT_TO_PROVIDER, CLIENT,
                CLIENT, 5, null, CLOSED, NOW)).hasMessageContaining("sí mismo");
        assertThatThrownBy(() -> ServiceEvaluation.register(null, EvaluationDirection.CLIENT_TO_PROVIDER, CLIENT,
                TECHNICIAN, 5, null, CLOSED, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServiceEvaluation.register(SERVICE, null, CLIENT, TECHNICIAN, 5, null, CLOSED,
                NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServiceEvaluation.register(SERVICE, EvaluationDirection.CLIENT_TO_PROVIDER, null,
                TECHNICIAN, 5, null, CLOSED, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServiceEvaluation.register(SERVICE, EvaluationDirection.CLIENT_TO_PROVIDER, CLIENT,
                null, 5, null, CLOSED, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServiceEvaluation.register(SERVICE, EvaluationDirection.CLIENT_TO_PROVIDER, CLIENT,
                TECHNICIAN, 5, null, null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServiceEvaluation.register(SERVICE, EvaluationDirection.CLIENT_TO_PROVIDER, CLIENT,
                TECHNICIAN, 5, null, CLOSED, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elEventoDelOutboxExigeSusDatos() {
        UUID id = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.pending(id, "evaluacion.registrada", (short) 1, "Evaluation", id, 1,
                "fixia.users.evaluations", TECHNICIAN.toString(), "{}", NOW);

        assertThat(event.isNew()).isTrue();
        assertThat(event.getId()).isEqualTo(id);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getSchemaVersion()).isEqualTo((short) 1);
        assertThat(event.getOccurredAt()).isEqualTo(NOW);
        assertThat(event.getEventType()).isEqualTo("evaluacion.registrada");
        assertThatThrownBy(() -> OutboxEvent.pending(null, "x", (short) 1, "E", id, 1, "t", "k", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboxEvent.pending(id, " ", (short) 1, "E", id, 1, "t", "k", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboxEvent.pending(id, "x", (short) 0, "E", id, 1, "t", "k", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboxEvent.pending(id, "x", (short) 1, "", id, 1, "t", "k", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboxEvent.pending(id, "x", (short) 1, "E", id, 1, "", "k", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboxEvent.pending(id, "x", (short) 1, "E", id, 1, "t", "k", "x".repeat(16001),
                NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboxEvent.pending(id, "x", (short) 1, "E", id, 1, "t", "k", "{}", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
