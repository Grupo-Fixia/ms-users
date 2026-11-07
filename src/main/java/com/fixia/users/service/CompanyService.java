package com.fixia.users.service;

import com.fixia.users.dto.CompanyResponse;
import com.fixia.users.dto.Reputation;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyTechnician;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.dto.CompanyPublicProfilesResponse;
import com.fixia.users.dto.CompanyTechnicianIdsResponse;
import com.fixia.users.dto.CompanyTechniciansResponse;
import com.fixia.users.exception.CompanyNotFoundException;
import com.fixia.users.repository.CompanyRepository;
import com.fixia.users.repository.CompanyTechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Identidad empresarial y técnicos asociados (GC-466). La empresa siempre se resuelve por la cuenta del token, nunca por
 * un identificador recibido en la petición: así una empresa no puede leer las asociaciones de otra. Las asociaciones no
 * se crean por API; salen de datos cargados de forma controlada (ver {@code db/seed}), de modo que nadie reclama una
 * cuenta ajena por conocer su id.
 */
@Service
public class CompanyService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyTechnicianRepository associationRepository;
    private final TechnicianPublicProfileService publicProfileService;
    private final ReputationService reputationService;

    public CompanyService(UserRepository userRepository, CompanyRepository companyRepository,
                          CompanyTechnicianRepository associationRepository,
                          TechnicianPublicProfileService publicProfileService,
                          ReputationService reputationService) {
        this.reputationService = reputationService;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.associationRepository = associationRepository;
        this.publicProfileService = publicProfileService;
    }

    @Transactional(readOnly = true)
    public Company get(UUID adminUserId) {
        return findCompany(adminUserId);
    }

    /** La empresa de la cuenta autenticada con su reputación (GC-551). */
    @Transactional(readOnly = true)
    public CompanyResponse getWithReputation(UUID adminUserId) {
        Company company = findCompany(adminUserId);
        Reputation reputation = reputationService.ofCompanies(Map.of(adminUserId, activeTechnicianIds(company)))
                .get(adminUserId);
        return CompanyResponse.from(company, reputation);
    }

    /**
     * Técnicos de la empresa de la cuenta autenticada. Se reutiliza el armado del perfil público (GC-425), que omite a
     * quien ya no tiene cuenta activa de técnico: una relación inconsistente no filtra filas ni datos.
     */
    @Transactional(readOnly = true)
    public CompanyTechniciansResponse listTechnicians(UUID adminUserId) {
        Company company = findCompany(adminUserId);
        List<UUID> technicianIds = associationRepository.findByCompanyIdOrderByCreatedAtAscIdAsc(company.getId())
                .stream()
                .map(CompanyTechnician::getTechnicianUserId)
                .toList();
        if (technicianIds.isEmpty()) {
            return new CompanyTechniciansResponse(List.of());
        }
        return new CompanyTechniciansResponse(publicProfileService.find(technicianIds).profiles());
    }

    /**
     * Para ms-services (GC-468). Verdadero solo si el administrador tiene cuenta activa con rol COMPANY y una empresa, el
     * técnico tiene cuenta activa con rol PROFESSIONAL y está asociado a esa empresa. No lanza errores: todo lo demás es
     * simplemente falso.
     */
    @Transactional(readOnly = true)
    public boolean isTechnicianAssociated(UUID adminUserId, UUID technicianUserId) {
        Optional<Company> company = findActive(adminUserId, Role.COMPANY)
                .flatMap(admin -> companyRepository.findByAdminUserId(admin.getId()));
        return company.isPresent()
                && findActive(technicianUserId, Role.PROFESSIONAL).isPresent()
                && associationRepository.existsByCompanyIdAndTechnicianUserId(company.get().getId(), technicianUserId);
    }

    /**
     * Técnicos activos de una empresa para ms-services (GC-468), que con ellos calcula las oportunidades de la empresa.
     * Solo si la cuenta es de una empresa activa; si no, lista vacía, igual que una empresa sin técnicos. Un técnico
     * cuya cuenta ya no está activa no cuenta.
     */
    @Transactional(readOnly = true)
    public CompanyTechnicianIdsResponse technicianIds(UUID adminUserId) {
        Optional<Company> company = findActive(adminUserId, Role.COMPANY)
                .flatMap(admin -> companyRepository.findByAdminUserId(admin.getId()));
        if (company.isEmpty()) {
            return new CompanyTechnicianIdsResponse(List.of());
        }
        return new CompanyTechnicianIdsResponse(activeTechnicianIds(company.get()));
    }

    /** Técnicos asociados a la empresa con cuenta activa de técnico, en el orden en que se asociaron. */
    private List<UUID> activeTechnicianIds(Company company) {
        List<UUID> associated = associationRepository.findByCompanyIdOrderByCreatedAtAscIdAsc(company.getId())
                .stream()
                .map(CompanyTechnician::getTechnicianUserId)
                .toList();
        Set<UUID> active = userRepository.findAllById(associated).stream()
                .filter(user -> user.getRole() == Role.PROFESSIONAL && user.getStatus() == UserStatus.ACTIVE)
                .map(User::getId)
                .collect(Collectors.toSet());
        return associated.stream().filter(active::contains).toList();
    }

    /**
     * Perfil público de varias empresas para ms-services (GC-468), que muestra al cliente quién se postuló. Una por cada
     * cuenta activa con rol COMPANY y empresa cargada, en el orden pedido y sin repetidos; las demás se omiten sin error.
     */
    @Transactional(readOnly = true)
    public CompanyPublicProfilesResponse publicProfiles(Collection<UUID> adminUserIds) {
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(adminUserIds));
        Set<UUID> activeAdmins = userRepository.findAllById(ids).stream()
                .filter(user -> user.getRole() == Role.COMPANY && user.getStatus() == UserStatus.ACTIVE)
                .map(User::getId)
                .collect(Collectors.toSet());
        Map<UUID, Company> companies = companyRepository.findByAdminUserIdIn(activeAdmins).stream()
                .collect(Collectors.toMap(Company::getAdminUserId, Function.identity()));
        // GC-551: reputación de cada empresa con sus técnicos actuales, en una sola consulta de evaluaciones.
        Map<UUID, List<UUID>> technicians = new java.util.HashMap<>();
        companies.forEach((adminId, company) -> technicians.put(adminId, activeTechnicianIds(company)));
        Map<UUID, Reputation> reputations = reputationService.ofCompanies(technicians);
        List<CompanyPublicProfilesResponse.Profile> profiles = new ArrayList<>();
        for (UUID id : ids) {
            Company company = companies.get(id);
            if (company != null) {
                profiles.add(new CompanyPublicProfilesResponse.Profile(id, company.getName(), reputations.get(id)));
            }
        }
        return new CompanyPublicProfilesResponse(profiles);
    }

    /**
     * Igual que las demás rutas "me": si la cuenta ya no existe, está deshabilitada o no es de una empresa, la sesión no
     * sirve y la respuesta es el mismo 401 de cualquier token inválido. Una cuenta de empresa sin empresa cargada es 404.
     */
    private Company findCompany(UUID adminUserId) {
        User admin = findActive(adminUserId, Role.COMPANY)
                .orElseThrow(() -> new BadCredentialsException("Sesión no válida"));
        return companyRepository.findByAdminUserId(admin.getId()).orElseThrow(CompanyNotFoundException::new);
    }

    private Optional<User> findActive(UUID userId, Role role) {
        return userRepository.findByIdAndRole(userId, role)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE);
    }
}
