package com.fixia.users.service;

import com.fixia.users.dto.Reputation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Regla de la reputación (GC-446, contrato GC-443 §7): promedio a un decimal con la mitad hacia arriba. */
class ReputationServiceTest {

    @Test
    void sinEvaluacionesNoHayReputacion() {
        assertThat(ReputationService.reputation(null, null)).isNull();
        assertThat(ReputationService.reputation(0L, 0L)).isNull();
    }

    @Test
    void redondeaAUnDecimalConLaMitadHaciaArriba() {
        assertThat(ReputationService.reputation(17L, 4L)).isEqualTo(new Reputation(new BigDecimal("4.3"), 4)); // 4,25
        assertThat(ReputationService.reputation(13L, 3L)).isEqualTo(new Reputation(new BigDecimal("4.3"), 3)); // 4,333…
        assertThat(ReputationService.reputation(14L, 3L)).isEqualTo(new Reputation(new BigDecimal("4.7"), 3)); // 4,666…
        assertThat(ReputationService.reputation(5L, 1L)).isEqualTo(new Reputation(new BigDecimal("5.0"), 1));
        assertThat(ReputationService.reputation(3L, 2L)).isEqualTo(new Reputation(new BigDecimal("1.5"), 2));
    }

    @Test
    void elPromedioSiempreQuedaEntreUnoYCinco() {
        assertThat(ReputationService.reputation(1L, 1L).average()).isEqualByComparingTo("1.0");
        assertThat(ReputationService.reputation(500L, 100L).average()).isEqualByComparingTo("5.0");
    }
}
