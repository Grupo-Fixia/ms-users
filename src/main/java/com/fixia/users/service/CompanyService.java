package com.fixia.users.service;

import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyTechnician;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.dto.CompanyTechniciansResponse;
import com.fixia.users.exception.CompanyNotFoundException;
import com.fixia.users.repository.CompanyRepository;
import com.fixia.users.repository.CompanyTechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    public CompanyService(UserRepository userRepository, CompanyRepository companyRepository,
                          CompanyTechnicianRepository associationRepository,
                          TechnicianPublicProfileService publicProfileService) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.associationRepository = associationRepository;
        this.publicProfileService = publicProfileService;
    }

    @Transactional(readOnly = true)
    public Company get(UUID adminUserId) {
        return findCompany(adminUserId);
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
