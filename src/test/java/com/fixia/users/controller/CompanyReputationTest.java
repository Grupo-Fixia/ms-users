package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyTechnician;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.EvaluationDirection;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.ServiceEvaluation;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.repository.CompanyRepository;
import com.fixia.users.repository.CompanyTechnicianRepository;
import com.fixia.users.repository.ServiceEvaluationRepository;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reputación de la empresa (GC-551, decisión del PO del 2026-11-06): promedio <b>simple</b> de las reputaciones de sus
 * técnicos actuales que tienen evaluaciones, sin ponderar por cuántas tiene cada uno.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompanyReputationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyTechnicianRepository associationRepository;

    @Autowired
    private ServiceEvaluationRepository evaluationRepository;

    private int documents;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch(); // en cascada: técnicos, empresas, asociaciones y evaluaciones
    }

    private User account(Role role) {
        documents++;
        return userRepository.save(new User("u" + documents + "-" + UUID.randomUUID() + "@example.com", "hash", role,
                "Laura", "Gómez", DocumentType.CC, "7" + documents + System.nanoTime() % 100000, "3104445566",
                "v1.0", Instant.now()));
    }

    private Company company(String name) {
        User admin = account(Role.COMPANY);
        return companyRepository.save(new Company(admin.getId(), name, "900100" + documents, Instant.now()));
    }

    private UUID technicianOf(Company company, int... ratings) {
        UUID technician = account(Role.PROFESSIONAL).getId();
        technicianRepository.save(new Technician(technician, Instant.now()));
        if (company != null) {
            associationRepository.save(new CompanyTechnician(company.getId(), technician, Instant.now()));
        }
        UUID client = account(Role.CLIENT).getId();
        Instant now = Instant.now();
        for (int rating : ratings) {
            evaluationRepository.save(ServiceEvaluation.register(UUID.randomUUID(),
                    EvaluationDirection.CLIENT_TO_PROVIDER, client, technician, rating, null, now.minusSeconds(60),
                    now));
        }
        return technician;
    }

    private org.springframework.test.web.servlet.ResultActions me(Company company) throws Exception {
        return mockMvc.perform(get("/api/users/companies/me")
                .with(jwt().jwt(token -> token.subject("usr_" + company.getAdminUserId()))
                        .authorities(new SimpleGrantedAuthority("ROLE_COMPANY"))));
    }

    private org.springframework.test.web.servlet.ResultActions publicProfiles(List<UUID> admins) throws Exception {
        return mockMvc.perform(post("/internal/companies/public-profiles")
                .with(InternalTechnicianControllerTest.internalService())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("companyAdminUserIds", admins))));
    }

    @Test
    void esElPromedioSimpleDeSusTecnicosSinPonderarPorEvaluaciones() throws Exception {
        Company andina = company("Servicios Andina");
        technicianOf(andina, 5, 5, 5, 5); // promedio 5
        technicianOf(andina, 3);          // promedio 3
        technicianOf(andina);             // sin evaluaciones: no cuenta

        // Simple: (5 + 3) / 2 = 4.0. Ponderado habría sido 23 / 5 = 4.6.
        me(andina)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Servicios Andina"))
                .andExpect(jsonPath("$.reputation.average").value(4.0))
                .andExpect(jsonPath("$.reputation.count").value(5));
    }

    @Test
    void sePromedianLosPromediosExactosYSeRedondeaAlFinal() throws Exception {
        Company norte = company("Reparaciones del Norte");
        technicianOf(norte, 5);       // 5
        technicianOf(norte, 4, 4, 5); // 4.333…

        // (5 + 4.333…) / 2 = 4.666… → 4.7. Redondeando cada técnico antes daría (5 + 4.3) / 2 = 4.65 → 4.7 también,
        // así que se fuerza un caso donde sí cambia:
        Company sur = company("Arreglos del Sur");
        technicianOf(sur, 4, 4, 5);    // 4.333…
        technicianOf(sur, 4, 5, 5, 5); // 4.75

        me(norte).andExpect(jsonPath("$.reputation.average").value(4.7));
        // Exacto: (4.333… + 4.75) / 2 = 4.541… → 4.5. Redondeando cada uno antes: (4.3 + 4.8) / 2 = 4.55 → 4.6.
        me(sur).andExpect(jsonPath("$.reputation.average").value(4.5));
    }

    @Test
    void sinTecnicosEvaluadosNoHayReputacion() throws Exception {
        Company nueva = company("Hogar Nuevo");
        technicianOf(nueva);

        me(nueva).andExpect(status().isOk()).andExpect(jsonPath("$.reputation").doesNotExist());
    }

    @Test
    void soloCuentanLosTecnicosActualesDeLaEmpresa() throws Exception {
        Company andina = company("Servicios Andina");
        technicianOf(andina, 2);
        technicianOf(null, 5, 5); // independiente: no es de la empresa

        me(andina).andExpect(jsonPath("$.reputation.average").value(2.0));
    }

    @Test
    void elPerfilPublicoQueVeMsServicesTraeLaReputacion() throws Exception {
        Company andina = company("Servicios Andina");
        technicianOf(andina, 5, 4);
        technicianOf(andina, 3);
        Company sinEvaluaciones = company("Hogar Nuevo");

        publicProfiles(List.of(andina.getAdminUserId(), sinEvaluaciones.getAdminUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles[0].name").value("Servicios Andina"))
                // (4.5 + 3) / 2 = 3.75 → 3.8 (ponderado habría sido 12 / 3 = 4.0).
                .andExpect(jsonPath("$.profiles[0].reputation.average").value(3.8))
                .andExpect(jsonPath("$.profiles[0].reputation.count").value(3))
                .andExpect(jsonPath("$.profiles[1].name").value("Hogar Nuevo"))
                .andExpect(jsonPath("$.profiles[1].reputation").doesNotExist());
    }
}
