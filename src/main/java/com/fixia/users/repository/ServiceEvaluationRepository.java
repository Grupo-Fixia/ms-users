package com.fixia.users.repository;

import com.fixia.users.domain.EvaluationDirection;
import com.fixia.users.domain.ServiceEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceEvaluationRepository extends JpaRepository<ServiceEvaluation, UUID> {

    /** La evaluación de ese servicio en esa dirección, si ya existe (una como máximo: restricción única). */
    Optional<ServiceEvaluation> findByServiceRequestIdAndDirection(UUID serviceRequestId, EvaluationDirection direction);

    /** Las evaluaciones de esos servicios en las que el usuario evaluó o fue evaluado, en orden de registro. */
    @Query("""
            select e from ServiceEvaluation e
            where e.serviceRequestId in :serviceRequestIds
              and (e.evaluatorId = :userId or e.evaluatedId = :userId)
            order by e.createdAt asc, e.id asc
            """)
    List<ServiceEvaluation> findVisibleTo(@Param("userId") UUID userId,
                                          @Param("serviceRequestIds") Collection<UUID> serviceRequestIds);
    /**
     * Suma y número de evaluaciones recibidas por cada persona en esa dirección (GC-446), en una sola consulta
     * agrupada que usa el índice {@code (evaluated_id, direction)}. Quien no tiene evaluaciones no aparece.
     */
    @Query("""
            select e.evaluatedId as evaluatedId, sum(e.rating) as ratingSum, count(e) as ratingCount
            from ServiceEvaluation e
            where e.evaluatedId in :evaluatedIds and e.direction = :direction
            group by e.evaluatedId
            """)
    List<ReputationTotals> totalsFor(@Param("evaluatedIds") Collection<UUID> evaluatedIds,
                                     @Param("direction") EvaluationDirection direction);
}
