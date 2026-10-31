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
}
