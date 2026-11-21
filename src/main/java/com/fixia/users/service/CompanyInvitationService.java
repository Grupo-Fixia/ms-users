package com.fixia.users.service;

import com.fixia.users.config.CompanyProperties;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyInvitation;
import com.fixia.users.domain.InvitationDisplayStatus;
import com.fixia.users.domain.InvitationStatus;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.dto.InvitationResponse;
import com.fixia.users.dto.InvitationsResponse;
import com.fixia.users.exception.InvitationConflictException;
import com.fixia.users.exception.InvitationNotFoundException;
import com.fixia.users.exception.NotInvitableException;
import com.fixia.users.repository.CompanyInvitationRepository;
import com.fixia.users.repository.CompanyTechnicianRepository;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Invitaciones de una empresa a técnicos independientes (GC-523, RF-017). La empresa siempre sale de la cuenta del
 * token ({@link CompanyService#get}), nunca de un identificador de la petición, y toda búsqueda de invitaciones va
 * acotada a esa empresa: un actor ajeno no ve ni toca las de otra.
 *
 * <p>Esta clase solo crea, lista y cancela. Aceptar, rechazar y desvincular (que crean y borran la asociación) son de
 * GC-524.
 */
@Service
public class CompanyInvitationService {

    private final CompanyService companyService;
    private final UserRepository userRepository;
    private final TechnicianRepository technicianRepository;
    private final CompanyTechnicianRepository associationRepository;
    private final CompanyInvitationRepository invitationRepository;
    private final CompanyProperties properties;
    private final Clock clock;

    public CompanyInvitationService(CompanyService companyService, UserRepository userRepository,
                                    TechnicianRepository technicianRepository,
                                    CompanyTechnicianRepository associationRepository,
                                    CompanyInvitationRepository invitationRepository,
                                    CompanyProperties properties, Clock clock) {
        this.companyService = companyService;
        this.userRepository = userRepository;
        this.technicianRepository = technicianRepository;
        this.associationRepository = associationRepository;
        this.invitationRepository = invitationRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Invita al técnico con ese correo exacto. Solo se invita a un técnico independiente: cuenta {@code PROFESSIONAL}
     * activa, con perfil y sin empresa. Los tres motivos de rechazo comparten respuesta ({@link NotInvitableException}).
     */
    @Transactional
    public InvitationResponse invite(UUID adminUserId, String technicianEmail) {
        Company company = companyService.get(adminUserId);
        User technician = findInvitable(technicianEmail).orElseThrow(NotInvitableException::new);
        Instant now = clock.instant();

        Optional<CompanyInvitation> pending = invitationRepository.findByCompanyIdAndTechnicianUserIdAndStatus(
                company.getId(), technician.getId(), InvitationStatus.PENDING);
        if (pending.isPresent()) {
            CompanyInvitation previous = pending.get();
            if (!previous.isExpired(now)) {
                throw InvitationConflictException.alreadyPending();
            }
            // Vencida pero todavía marcada como pendiente: se cierra para liberar la restricción única y reinvitar.
            previous.closeAsExpired(now);
            invitationRepository.saveAndFlush(previous);
        }

        CompanyInvitation invitation = new CompanyInvitation(company.getId(), technician.getId(), now,
                now.plus(properties.invitationTtl()));
        try {
            invitationRepository.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException e) {
            // Dos invitaciones simultáneas al mismo técnico: la base dejó pasar solo una.
            throw InvitationConflictException.alreadyPending();
        }
        return toResponse(invitation, technician, now);
    }

    /** Invitaciones de la empresa, la más reciente primero, con filtro opcional por el estado que se muestra. */
    @Transactional(readOnly = true)
    public InvitationsResponse list(UUID adminUserId, InvitationDisplayStatus status) {
        Company company = companyService.get(adminUserId);
        Instant now = clock.instant();
        List<CompanyInvitation> invitations = invitationRepository
                .findByCompanyIdOrderByCreatedAtDescIdDesc(company.getId()).stream()
                .filter(invitation -> status == null || invitation.displayStatus(now) == status)
                .toList();
        Map<UUID, User> technicians = userRepository
                .findAllById(invitations.stream().map(CompanyInvitation::getTechnicianUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return new InvitationsResponse(invitations.stream()
                .map(invitation -> toResponse(invitation, technicians.get(invitation.getTechnicianUserId()), now))
                .toList());
    }

    /**
     * Cancela una invitación pendiente de la empresa. Una que no existe o es de otra empresa es 404; una que ya no
     * está pendiente (respondida, cancelada o vencida) es 409.
     */
    @Transactional
    public void cancel(UUID adminUserId, UUID invitationId) {
        Company company = companyService.get(adminUserId);
        CompanyInvitation invitation = invitationRepository.findByIdAndCompanyId(invitationId, company.getId())
                .orElseThrow(InvitationNotFoundException::new);
        Instant now = clock.instant();
        if (!invitation.isOpen(now)) {
            throw InvitationConflictException.notPending();
        }
        invitation.cancelByCompany(now);
        invitationRepository.save(invitation);
    }

    private Optional<User> findInvitable(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalized)
                .filter(user -> user.getRole() == Role.PROFESSIONAL && user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> technicianRepository.findByUserId(user.getId()).isPresent())
                .filter(user -> !associationRepository.existsByTechnicianUserId(user.getId()));
    }

    private static InvitationResponse toResponse(CompanyInvitation invitation, User technician, Instant now) {
        String displayName = technician == null ? ""
                : TechnicianPublicProfileService.displayName(technician.getFirstName(), technician.getLastName());
        return new InvitationResponse(invitation.getId(), invitation.displayStatus(now),
                new InvitationResponse.Technician(displayName), invitation.getCreatedAt(), invitation.getExpiresAt(),
                invitation.getRespondedAt());
    }
}
