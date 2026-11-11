package com.fixia.users.repository;

import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyInvitation;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.InvitationStatus;
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

/** La restricción de «una sola pendiente por empresa y técnico» vive en la base (V12), no solo en el servicio. */
@SpringBootTest
@ActiveProfiles("test")
class CompanyInvitationRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyInvitationRepository invitationRepository;

    private Company company;
    private Company otherCompany;
    private UUID technicianUserId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        company = company("empresa-a@fixia.test", "100");
        otherCompany = company("empresa-b@fixia.test", "101");
        technicianUserId = userRepository.save(new User("tecnico@fixia.test", "hash", Role.PROFESSIONAL, "Carlos",
                "Giraldo", DocumentType.CC, "200", "3104445566", "v1.0", NOW)).getId();
        technicianRepository.save(new Technician(technicianUserId, NOW));
    }

    private Company company(String email, String document) {
        User admin = userRepository.save(new User(email, "hash", Role.COMPANY, "Laura", "Gómez", DocumentType.CC,
                document, "3104445566", "v1.0", NOW));
        return companyRepository.save(new Company(admin.getId(), "Empresa " + document, null, NOW));
    }

    private CompanyInvitation invitation(Company forCompany) {
        return new CompanyInvitation(forCompany.getId(), technicianUserId, NOW, NOW.plusSeconds(3600));
    }

    @Test
    void noPermiteDosPendientesDeLaMismaEmpresaAlMismoTecnico() {
        invitationRepository.saveAndFlush(invitation(company));
        assertThatThrownBy(() -> invitationRepository.saveAndFlush(invitation(company)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void otraEmpresaSiPuedeTenerSuPropiaPendienteAlMismoTecnico() {
        invitationRepository.saveAndFlush(invitation(company));
        invitationRepository.saveAndFlush(invitation(otherCompany));
        assertThat(invitationRepository.count()).isEqualTo(2);
    }

    @Test
    void lasResueltasSeAcumulanYPermitenUnaPendienteNueva() {
        for (int i = 0; i < 3; i++) {
            CompanyInvitation old = invitation(company);
            invitationRepository.saveAndFlush(old);
            old.cancelByCompany(NOW.plusSeconds(10));
            invitationRepository.saveAndFlush(old);
        }
        invitationRepository.saveAndFlush(invitation(company));

        assertThat(invitationRepository.findByCompanyIdOrderByCreatedAtDescIdDesc(company.getId())).hasSize(4);
        assertThat(invitationRepository.findByCompanyIdAndTechnicianUserIdAndStatus(company.getId(),
                technicianUserId, InvitationStatus.PENDING)).isPresent();
    }

    @Test
    void unaInvitacionAjenaNoSeEncuentraPorId() {
        CompanyInvitation saved = invitationRepository.saveAndFlush(invitation(company));
        assertThat(invitationRepository.findByIdAndCompanyId(saved.getId(), company.getId())).isPresent();
        assertThat(invitationRepository.findByIdAndCompanyId(saved.getId(), otherCompany.getId())).isEmpty();
    }
}
