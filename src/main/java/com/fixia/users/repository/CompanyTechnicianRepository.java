package com.fixia.users.repository;

import com.fixia.users.domain.CompanyTechnician;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyTechnicianRepository extends JpaRepository<CompanyTechnician, UUID> {

    /** Asociaciones de la empresa en un orden estable: por fecha de alta y, a igual fecha, por id. */
    List<CompanyTechnician> findByCompanyIdOrderByCreatedAtAscIdAsc(UUID companyId);

    boolean existsByCompanyIdAndTechnicianUserId(UUID companyId, UUID technicianUserId);
}
