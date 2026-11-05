package com.fixia.users.repository;

import com.fixia.users.domain.OutboxEvent;
import com.fixia.users.domain.OutboxStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Los eventos de un agregado en el orden de su historia. */
    List<OutboxEvent> findByAggregateIdOrderByAggregateSequenceAsc(UUID aggregateId);

    /**
     * Eventos pendientes listos para publicar (GC-500, la misma consulta que ms-services): {@code PENDING}, con su
     * reintento vencido y en orden estricto por agregado (no sale la secuencia n+1 mientras la anterior no esté
     * publicada). {@code FOR UPDATE SKIP LOCKED} permite varias réplicas sin publicar dos veces.
     */
    @Query(value = """
            SELECT * FROM outbox_events e
            WHERE e.status = 'PENDING'
              AND e.next_attempt_at <= :now
              AND NOT EXISTS (
                  SELECT 1 FROM outbox_events prev
                  WHERE prev.aggregate_id = e.aggregate_id
                    AND prev.aggregate_sequence < e.aggregate_sequence
                    AND prev.status != 'PUBLISHED'
              )
            ORDER BY e.occurred_at ASC, e.aggregate_sequence ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findPendingForRelay(@Param("now") Instant now, @Param("limit") int limit);

    /** Conteo por estado, para las métricas del relay. */
    long countByStatus(OutboxStatus status);

    /** El más antiguo en un estado, para la edad del pendiente más viejo. */
    Optional<OutboxEvent> findFirstByStatusOrderByOccurredAtAsc(OutboxStatus status);
}
