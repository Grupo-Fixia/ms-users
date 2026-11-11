package com.fixia.users.repository;

import com.fixia.users.domain.CompanyInvitation;
import com.fixia.users.domain.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, UUID> {

    /** Invitaciones de la empresa, de la más reciente a la más antigua y, a igual fecha, por id para un orden estable. */
    List<CompanyInvitation> findByCompanyIdOrderByCreatedAtDescIdDesc(UUID companyId);

    /** Siempre acotada a la empresa: una invitación ajena es indistinguible de una que no existe. */
    Optional<CompanyInvitation> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<CompanyInvitation> findByCompanyIdAndTechnicianUserIdAndStatus(UUID companyId, UUID technicianUserId,
                                                                           InvitationStatus status);
}
