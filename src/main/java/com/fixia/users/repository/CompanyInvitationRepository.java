package com.fixia.users.repository;

import com.fixia.users.domain.CompanyInvitation;
import com.fixia.users.domain.InvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, UUID> {

    /** Invitaciones de la empresa, de la más reciente a la más antigua y, a igual fecha, por id para un orden estable. */
    List<CompanyInvitation> findByCompanyIdOrderByCreatedAtDescIdDesc(UUID companyId);

    /** Siempre acotada a la empresa: una invitación ajena es indistinguible de una que no existe. */
    Optional<CompanyInvitation> findByIdAndCompanyId(UUID id, UUID companyId);

    /**
     * La misma búsqueda con la fila bloqueada hasta el fin de la transacción (GC-524): la empresa que cancela y el
     * técnico que responde no se pisan; el segundo ve el estado que dejó el primero.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CompanyInvitation> findLockedByIdAndCompanyId(UUID id, UUID companyId);

    /** Una invitación recibida por el técnico (GC-524). Una ajena es como si no existiera. */
    Optional<CompanyInvitation> findByIdAndTechnicianUserId(UUID id, UUID technicianUserId);

    /** Una invitación recibida por el técnico, bloqueada para responderla (GC-524). Una ajena es como si no existiera. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CompanyInvitation> findLockedByIdAndTechnicianUserId(UUID id, UUID technicianUserId);

    /**
     * Bloquea todas las invitaciones del técnico en ese estado, siempre en el mismo orden (por id), hasta el fin de la
     * transacción (GC-524). Al aceptar, cada transacción las toma antes de tocar nada: dos aceptaciones simultáneas del
     * mismo técnico se ordenan en lugar de bloquearse mutuamente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<CompanyInvitation> findLockedByTechnicianUserIdAndStatusOrderByIdAsc(UUID technicianUserId,
                                                                             InvitationStatus status);

    /** Invitaciones del técnico en un estado, la más reciente primero (GC-524). */
    List<CompanyInvitation> findByTechnicianUserIdAndStatusOrderByCreatedAtDescIdDesc(UUID technicianUserId,
                                                                                       InvitationStatus status);

    Optional<CompanyInvitation> findByCompanyIdAndTechnicianUserIdAndStatus(UUID companyId, UUID technicianUserId,
                                                                           InvitationStatus status);
}
