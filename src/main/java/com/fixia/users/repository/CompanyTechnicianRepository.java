package com.fixia.users.repository;

import com.fixia.users.domain.CompanyTechnician;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyTechnicianRepository extends JpaRepository<CompanyTechnician, UUID> {

    /** Asociaciones de la empresa en un orden estable: por fecha de alta y, a igual fecha, por id. */
    List<CompanyTechnician> findByCompanyIdOrderByCreatedAtAscIdAsc(UUID companyId);

    boolean existsByCompanyIdAndTechnicianUserId(UUID companyId, UUID technicianUserId);

    /** Un técnico está en una sola empresa: sirve para saber si sigue siendo independiente (GC-523). */
    boolean existsByTechnicianUserId(UUID technicianUserId);

    /** La empresa a la que pertenece el técnico, si pertenece a alguna (GC-524). */
    Optional<CompanyTechnician> findByTechnicianUserId(UUID technicianUserId);
}
