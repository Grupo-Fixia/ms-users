package com.fixia.users.service;

import com.fixia.users.client.CompanyWorkloadClient;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyInvitation;
import com.fixia.users.domain.CompanyMembershipEvent;
import com.fixia.users.domain.CompanyTechnician;
import com.fixia.users.domain.InvitationStatus;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.dto.MembershipResponse;
import com.fixia.users.dto.ReceivedInvitationResponse;
import com.fixia.users.exception.InvitationConflictException;
import com.fixia.users.exception.InvitationNotFoundException;
import com.fixia.users.exception.LeaveBlockedException;
import com.fixia.users.exception.NotCompanyMemberException;
import com.fixia.users.repository.CompanyInvitationRepository;
import com.fixia.users.repository.CompanyMembershipEventRepository;
import com.fixia.users.repository.CompanyRepository;
import com.fixia.users.repository.CompanyTechnicianRepository;
import com.fixia.users.repository.OutboxEventRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El lado del técnico en las invitaciones empresariales (GC-524, contrato de empresas §5): ver las que recibió,
 * aceptarlas o rechazarlas, ver su empresa y desvincularse. El técnico siempre sale del token; una invitación ajena es
 * indistinguible de una que no existe.
 *
 * <ul>
 *   <li>La asociación ({@link CompanyTechnician}) solo nace al aceptar (D4) y solo el técnico la termina (D5, P1).</li>
 *   <li>Cada vinculación y desvinculación queda en el historial ({@link CompanyMembershipEvent}, D7) y sale como
 *   evento por el Outbox en la misma transacción (D11).</li>
 *   <li>Para desvincularse se pregunta a ms-services y a ms-payments; si alguno no responde, no se desvincula (D6).</li>
 * </ul>
 */
@Service
public class TechnicianMembershipService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyTechnicianRepository associationRepository;
    private final CompanyInvitationRepository invitationRepository;
    private final CompanyMembershipEventRepository historyRepository;
    private final OutboxEventRepository outboxRepository;
    private final CompanyMembershipEvents events;
    private final CompanyWorkloadClient workloadClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public TechnicianMembershipService(UserRepository userRepository, CompanyRepository companyRepository,
                                       CompanyTechnicianRepository associationRepository,
                                       CompanyInvitationRepository invitationRepository,
                                       CompanyMembershipEventRepository historyRepository,
                                       OutboxEventRepository outboxRepository, CompanyMembershipEvents events,
                                       CompanyWorkloadClient workloadClient, TransactionTemplate transactionTemplate,
                                       Clock clock) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.associationRepository = associationRepository;
        this.invitationRepository = invitationRepository;
        this.historyRepository = historyRepository;
        this.outboxRepository = outboxRepository;
        this.events = events;
        this.workloadClient = workloadClient;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /** Invitaciones pendientes y vigentes del técnico, de empresas que siguen activas, la más reciente primero (§5.1). */
    @Transactional(readOnly = true)
    public ReceivedInvitationResponse.Page received(UUID technicianUserId) {
        requireTechnician(technicianUserId);
        Instant now = clock.instant();
        List<CompanyInvitation> open = invitationRepository
                .findByTechnicianUserIdAndStatusOrderByCreatedAtDescIdDesc(technicianUserId, InvitationStatus.PENDING)
                .stream()
                .filter(invitation -> invitation.isOpen(now))
                .toList();
        Map<UUID, Company> companies = activeCompanies(open.stream().map(CompanyInvitation::getCompanyId).toList());
        return new ReceivedInvitationResponse.Page(open.stream()
                .filter(invitation -> companies.containsKey(invitation.getCompanyId()))
                .map(invitation -> toResponse(invitation, companies.get(invitation.getCompanyId()), now))
                .toList());
    }

    /**
     * Acepta la invitación (§5.2), todo en una transacción: crea la asociación, marca la invitación aceptada, cancela
     * las demás abiertas del técnico, registra {@code JOINED} y escribe {@code empresa.tecnico_vinculado}. Si ya
     * pertenece a una empresa, la restricción única de {@code company_technicians} lo frena aunque dos aceptaciones
     * lleguen a la vez.
     */
    @Transactional
    public MembershipResponse accept(UUID technicianUserId, UUID invitationId) {
        requireTechnician(technicianUserId);
        // Primero se bloquean todas sus pendientes y en orden: aceptar toca las demás (las cancela), así que dos
        // aceptaciones simultáneas se esperan en vez de bloquearse mutuamente. La segunda ve la suya ya cancelada.
        List<CompanyInvitation> pending = invitationRepository.findLockedByTechnicianUserIdAndStatusOrderByIdAsc(
                technicianUserId, InvitationStatus.PENDING);
        CompanyInvitation invitation = pending.stream()
                .filter(candidate -> candidate.getId().equals(invitationId))
                .findFirst()
                .orElse(null);
        Instant now = clock.instant();
        if (invitation == null) {
            // No está pendiente: o no es suya (404) o ya se respondió, canceló o venció (409).
            invitationRepository.findByIdAndTechnicianUserId(invitationId, technicianUserId)
                    .orElseThrow(InvitationNotFoundException::new);
            throw InvitationConflictException.notPending();
        }
        if (!invitation.isOpen(now)) {
            throw InvitationConflictException.notPending();
        }
        if (associationRepository.existsByTechnicianUserId(technicianUserId)) {
            throw InvitationConflictException.alreadyInCompany();
        }
        Company company = activeCompanies(List.of(invitation.getCompanyId())).get(invitation.getCompanyId());
        if (company == null) {
            throw InvitationConflictException.companyUnavailable();
        }

        CompanyTechnician membership;
        try {
            membership = associationRepository.saveAndFlush(
                    new CompanyTechnician(company.getId(), technicianUserId, now));
        } catch (DataIntegrityViolationException e) {
            // Otra aceptación del mismo técnico ganó la carrera.
            throw InvitationConflictException.alreadyInCompany();
        }
        invitation.accept(now);
        invitationRepository.save(invitation);
        for (CompanyInvitation other : pending) {
            if (other != invitation && other.isOpen(now)) {
                other.cancelBecauseJoinedOtherCompany(now);
                invitationRepository.save(other);
            }
        }
        CompanyMembershipEvent joined = historyRepository.save(
                CompanyMembershipEvent.joined(membership, invitation.getId(), now));
        outboxRepository.save(events.joined(joined, company));
        return new MembershipResponse(new ReceivedInvitationResponse.CompanyName(company.getName()),
                membership.getCreatedAt());
    }

    /** Rechaza la invitación (§5.3). La asociación no cambia. */
    @Transactional
    public ReceivedInvitationResponse reject(UUID technicianUserId, UUID invitationId) {
        requireTechnician(technicianUserId);
        CompanyInvitation invitation = invitationRepository.findLockedByIdAndTechnicianUserId(invitationId,
                technicianUserId).orElseThrow(InvitationNotFoundException::new);
        Instant now = clock.instant();
        if (!invitation.isOpen(now)) {
            throw InvitationConflictException.notPending();
        }
        invitation.reject(now);
        invitationRepository.save(invitation);
        Company company = companyRepository.findById(invitation.getCompanyId()).orElse(null);
        return toResponse(invitation, company, now);
    }

    /** La empresa del técnico (§5.4); 404 si es independiente. */
    @Transactional(readOnly = true)
    public MembershipResponse company(UUID technicianUserId) {
        requireTechnician(technicianUserId);
        CompanyTechnician membership = associationRepository.findByTechnicianUserId(technicianUserId)
                .orElseThrow(NotCompanyMemberException::new);
        Company company = companyRepository.findById(membership.getCompanyId())
                .orElseThrow(NotCompanyMemberException::new);
        return new MembershipResponse(new ReceivedInvitationResponse.CompanyName(company.getName()),
                membership.getCreatedAt());
    }

    /**
     * Desvincula al técnico de su empresa (§5.5). Primero pregunta, fuera de toda transacción, por su trabajo pendiente
     * (ms-services) y por el dinero retenido en los servicios que ejecutó (ms-payments). Si hay algo, responde los
     * motivos y no cambia nada. Si no, en una transacción borra la asociación, registra {@code LEFT} y escribe
     * {@code empresa.tecnico_desvinculado}.
     *
     * <p>Entre la consulta y el borrado la empresa podría postularse con este técnico: ms-services vuelve a comprobar la
     * asociación al seleccionar, cotizar y contratar (§7), así que esa postulación no prospera.
     */
    public void leave(UUID technicianUserId) {
        requireTechnician(technicianUserId);
        CompanyTechnician membership = associationRepository.findByTechnicianUserId(technicianUserId)
                .orElseThrow(NotCompanyMemberException::new);
        Company company = companyRepository.findById(membership.getCompanyId())
                .orElseThrow(NotCompanyMemberException::new);

        CompanyWorkloadClient.Workload workload = workloadClient.workload(company.getAdminUserId(), technicianUserId);
        List<UUID> held = workloadClient.held(company.getAdminUserId(), workload.serviceRequestIds());
        List<String> reasons = new ArrayList<>();
        if (!workload.pendingServiceRequestIds().isEmpty()) {
            reasons.add(LeaveBlockedException.PENDING_WORK);
        }
        if (!held.isEmpty()) {
            reasons.add(LeaveBlockedException.HELD_BALANCE);
        }
        if (!reasons.isEmpty()) {
            Set<UUID> blocking = new LinkedHashSet<>(workload.pendingServiceRequestIds());
            blocking.addAll(held);
            throw new LeaveBlockedException(reasons, List.copyOf(blocking));
        }

        transactionTemplate.executeWithoutResult(status -> {
            // Si otra petición ya lo desvinculó mientras se consultaba, no hay nada que borrar.
            CompanyTechnician current = associationRepository.findById(membership.getId())
                    .orElseThrow(NotCompanyMemberException::new);
            associationRepository.delete(current);
            associationRepository.flush();
            CompanyMembershipEvent left = historyRepository.save(
                    CompanyMembershipEvent.left(current, clock.instant()));
            outboxRepository.save(events.left(left, company));
        });
    }

    /**
     * Igual que las demás rutas "me": si la cuenta ya no existe, está deshabilitada o no es de un técnico, la sesión no
     * sirve y la respuesta es el mismo 401 de cualquier token inválido.
     */
    private void requireTechnician(UUID technicianUserId) {
        userRepository.findByIdAndRole(technicianUserId, Role.PROFESSIONAL)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("Sesión no válida"));
    }

    /** Empresas cuya cuenta administradora sigue activa: una deshabilitada no invita ni recibe técnicos. */
    private Map<UUID, Company> activeCompanies(List<UUID> companyIds) {
        if (companyIds.isEmpty()) {
            return Map.of();
        }
        List<Company> companies = companyRepository.findAllById(Set.copyOf(companyIds));
        Set<UUID> activeAdmins = userRepository.findAllById(companies.stream().map(Company::getAdminUserId).toList())
                .stream()
                .filter(user -> user.getRole() == Role.COMPANY && user.getStatus() == UserStatus.ACTIVE)
                .map(User::getId)
                .collect(Collectors.toSet());
        return companies.stream()
                .filter(company -> activeAdmins.contains(company.getAdminUserId()))
                .collect(Collectors.toMap(Company::getId, Function.identity()));
    }

    private static ReceivedInvitationResponse toResponse(CompanyInvitation invitation, Company company, Instant now) {
        String name = Optional.ofNullable(company).map(Company::getName).orElse("");
        return new ReceivedInvitationResponse(invitation.getId(), invitation.displayStatus(now),
                new ReceivedInvitationResponse.CompanyName(name), invitation.getCreatedAt(), invitation.getExpiresAt(),
                invitation.getRespondedAt());
    }
}
