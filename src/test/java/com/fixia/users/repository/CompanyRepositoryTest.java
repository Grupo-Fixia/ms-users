package com.fixia.users.repository;

import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyTechnician;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Restricciones de la base para empresas y asociaciones (GC-466): no dependen de que el servicio las respete. */
@SpringBootTest
@ActiveProfiles("test")
class CompanyRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyTechnicianRepository associationRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
    }

    private User account(String email, String document, Role role) {
        return userRepository.save(new User(email, "hash", role, "Laura", "Gómez", DocumentType.CC, document,
                "3104445566", "v1.0", Instant.now()));
    }

    private Company company(String email, String document) {
        User admin = account(email, document, Role.COMPANY);
        return companyRepository.save(new Company(admin.getId(), "Empresa " + document, "900", Instant.now()));
    }

    private UUID technician(String email, String document) {
        UUID userId = account(email, document, Role.PROFESSIONAL).getId();
        technicianRepository.save(new Technician(userId, Instant.now()));
        return userId;
    }

    @Test
    void guardaYRecuperaLaEmpresaPorLaCuentaDelAdministrador() {
        Company company = company("andina@example.com", "900100");

        Company found = companyRepository.findByAdminUserId(company.getAdminUserId()).orElseThrow();

        assertThat(found.getId()).isEqualTo(company.getId());
        assertThat(found.getName()).isEqualTo("Empresa 900100");
        assertThat(found.getNit()).isEqualTo("900");
        assertThat(companyRepository.findByAdminUserId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void unaCuentaNoPuedeAdministrarDosEmpresas() {
        Company company = company("andina@example.com", "900100");

        assertThatThrownBy(() -> companyRepository.saveAndFlush(
                new Company(company.getAdminUserId(), "Otra empresa", null, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laEmpresaExigeAUnaCuentaExistente() {
        assertThatThrownBy(() -> companyRepository.saveAndFlush(
                new Company(UUID.randomUUID(), "Fantasma", null, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elNombreEsObligatorioYElNitSeNormaliza() {
        UUID admin = account("andina@example.com", "900100", Role.COMPANY).getId();

        assertThatThrownBy(() -> new Company(admin, "  ", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Company(admin, "  Andina ", "   ", Instant.now()).getNit()).isNull();
        assertThat(new Company(admin, "  Andina ", "  900  ", Instant.now()).getName()).isEqualTo("Andina");
    }

    @Test
    void unTecnicoSoloPuedeEstarAsociadoAUnaEmpresa() {
        Company first = company("andina@example.com", "900100");
        Company second = company("valle@example.com", "900200");
        UUID technician = technician("carlos@example.com", "2001");
        associationRepository.saveAndFlush(new CompanyTechnician(first.getId(), technician, Instant.now()));

        assertThatThrownBy(() -> associationRepository.saveAndFlush(
                new CompanyTechnician(second.getId(), technician, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> associationRepository.saveAndFlush(
                new CompanyTechnician(first.getId(), technician, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laAsociacionExigeUnaEmpresaYUnPerfilDeTecnicoExistentes() {
        Company company = company("andina@example.com", "900100");
        UUID withoutTechnicianProfile = account("sinperfil@example.com", "2002", Role.PROFESSIONAL).getId();

        assertThatThrownBy(() -> associationRepository.saveAndFlush(
                new CompanyTechnician(company.getId(), UUID.randomUUID(), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> associationRepository.saveAndFlush(
                new CompanyTechnician(company.getId(), withoutTechnicianProfile, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> associationRepository.saveAndFlush(
                new CompanyTechnician(UUID.randomUUID(), technician("carlos@example.com", "2001"), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void consultaLasAsociacionesDeUnaEmpresaEnOrdenDeAlta() {
        Company company = company("andina@example.com", "900100");
        UUID later = technician("diana@example.com", "2003");
        UUID earlier = technician("carlos@example.com", "2001");
        associationRepository.save(new CompanyTechnician(company.getId(), later, Instant.parse("2026-10-02T10:00:00Z")));
        associationRepository.save(new CompanyTechnician(company.getId(), earlier, Instant.parse("2026-10-01T10:00:00Z")));

        assertThat(associationRepository.findByCompanyIdOrderByCreatedAtAscIdAsc(company.getId()))
                .extracting(CompanyTechnician::getTechnicianUserId)
                .containsExactly(earlier, later);
        assertThat(associationRepository.existsByCompanyIdAndTechnicianUserId(company.getId(), earlier)).isTrue();
        assertThat(associationRepository.existsByCompanyIdAndTechnicianUserId(UUID.randomUUID(), earlier)).isFalse();
    }

    @Test
    void borrarLaCuentaDelTecnicoOLaDelAdministradorLimpiaLasAsociaciones() {
        Company company = company("andina@example.com", "900100");
        UUID gone = technician("carlos@example.com", "2001");
        UUID stays = technician("diana@example.com", "2003");
        associationRepository.save(new CompanyTechnician(company.getId(), gone, Instant.now()));
        associationRepository.save(new CompanyTechnician(company.getId(), stays, Instant.now()));

        userRepository.deleteById(gone);
        assertThat(associationRepository.findByCompanyIdOrderByCreatedAtAscIdAsc(company.getId()))
                .extracting(CompanyTechnician::getTechnicianUserId).containsExactly(stays);

        userRepository.deleteById(company.getAdminUserId());
        assertThat(companyRepository.findById(company.getId())).isEmpty();
        assertThat(associationRepository.findAll()).isEmpty();
        assertThat(technicianRepository.findByUserId(stays)).isPresent();
    }
}
