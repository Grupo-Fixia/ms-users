package com.fixia.users.service;

import com.fixia.users.domain.EvaluationDirection;
import com.fixia.users.dto.Reputation;
import com.fixia.users.repository.ReputationTotals;
import com.fixia.users.repository.ServiceEvaluationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reputación calculada al consultar (GC-446, opción A del contrato GC-443 §7). La fuente es la tabla de evaluaciones
 * originales, de la que {@code ms-users} es custodio: no hay proyección que deduplicar ni reconstruir, una reentrega
 * del evento {@code evaluacion.registrada} no cambia nada porque aquí no se leen eventos, y como las evaluaciones son
 * inmutables y únicas por servicio y dirección, el conteo no se puede inflar.
 * <p>
 * La reputación de un técnico es la de sus evaluaciones {@code CLIENT_TO_PROVIDER}; la del cliente
 * ({@code PROVIDER_TO_CLIENT}) se calcula con la misma regla, pero no se muestra en ninguna pantalla en este sprint.
 */
@Service
public class ReputationService {

    private final ServiceEvaluationRepository evaluationRepository;

    public ReputationService(ServiceEvaluationRepository evaluationRepository) {
        this.evaluationRepository = evaluationRepository;
    }

    /** Reputación como prestador de cada persona pedida; quien no tiene evaluaciones no aparece en el mapa. */
    @Transactional(readOnly = true)
    public Map<UUID, Reputation> asProvider(Collection<UUID> userIds) {
        return of(userIds, EvaluationDirection.CLIENT_TO_PROVIDER);
    }

    /** Reputación como prestador de una persona, o {@code null} si todavía nadie la ha evaluado. */
    @Transactional(readOnly = true)
    public Reputation asProvider(UUID userId) {
        return asProvider(List.of(userId)).get(userId);
    }

    /** Reputación como cliente, con la misma regla, para cuando una pantalla la necesite. */
    @Transactional(readOnly = true)
    public Map<UUID, Reputation> asClient(Collection<UUID> userIds) {
        return of(userIds, EvaluationDirection.PROVIDER_TO_CLIENT);
    }

    private Map<UUID, Reputation> of(Collection<UUID> userIds, EvaluationDirection direction) {
        Map<UUID, Reputation> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        for (ReputationTotals totals : evaluationRepository.totalsFor(new LinkedHashSet<>(userIds), direction)) {
            Reputation reputation = reputation(totals.getRatingSum(), totals.getRatingCount());
            if (reputation != null) {
                result.put(totals.getEvaluatedId(), reputation);
            }
        }
        return result;
    }

    /**
     * Promedio exacto (suma entre número, en decimal) redondeado a un decimal con la mitad hacia arriba: 4,25 → 4,3.
     * Sin evaluaciones no hay reputación.
     */
    static Reputation reputation(Long ratingSum, Long ratingCount) {
        if (ratingSum == null || ratingCount == null || ratingCount <= 0) {
            return null;
        }
        BigDecimal average = BigDecimal.valueOf(ratingSum)
                .divide(BigDecimal.valueOf(ratingCount), 1, RoundingMode.HALF_UP);
        return new Reputation(average, ratingCount);
    }
}
