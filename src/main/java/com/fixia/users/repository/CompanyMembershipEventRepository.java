package com.fixia.users.repository;

import com.fixia.users.domain.CompanyMembershipEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Historial de vinculaciones (GC-524). Solo se inserta; no hay borrado ni edición desde el código. */
public interface CompanyMembershipEventRepository extends JpaRepository<CompanyMembershipEvent, UUID> {

    List<CompanyMembershipEvent> findByTechnicianUserIdOrderByOccurredAtAsc(UUID technicianUserId);
}
