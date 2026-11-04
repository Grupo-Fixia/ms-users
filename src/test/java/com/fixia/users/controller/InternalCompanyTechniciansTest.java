package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyTechnician;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.repository.CompanyRepository;
import com.fixia.users.repository.CompanyTechnicianRepository;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Técnicos de una empresa para ms-services (GC-468): solo los técnicos activos de una empresa activa, en el orden en que
 * se asociaron, y lista vacía en cualquier otro caso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalCompanyTechniciansTest {

    private static final String PATH = "/internal/companies/technicians";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private CompanyTechnicianRepository associationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final Instant base = Instant.parse("2026-10-30T12:00:00Z");

    @BeforeEach
    void cleanDatabase() {
        // El borrado en cascada se lleva técnicos, empresas y asociaciones.
        userRepository.deleteAllInBatch();
    }

    // ---------- Utilidades ----------

    private User account(String email, String document, Role role) {
        return userRepository.save(new User(email, "hash", role, "Laura", "Gómez", DocumentType.CC, document,
                "3104445566", "v1.0", Instant.now()));
    }

    private Company company(String email, String document) {
        User admin = account(email, document, Role.COMPANY);
        return companyRepository.save(new Company(admin.getId(), "Servicios Andina", "900" + document, Instant.now()));
    }

    private UUID associate(Company company, String email, String document, int minute) {
        UUID technician = account(email, document, Role.PROFESSIONAL).getId();
        technicianRepository.save(new Technician(technician, Instant.now()));
        associationRepository.save(new CompanyTechnician(company.getId(), technician, base.plusSeconds(60L * minute)));
        return technician;
    }

    private void disable(UUID userId) {
        transactionTemplate.executeWithoutResult(status -> userRepository.findById(userId).orElseThrow().disable());
    }

    private ResultActions find(Object companyAdminUserId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("companyAdminUserId", companyAdminUserId);
        return mockMvc.perform(post(PATH).with(InternalTechnicianControllerTest.internalService())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    // ---------- Contenido ----------

    @Test
    void devuelveLosTecnicosActivosEnElOrdenEnQueSeAsociaron() throws Exception {
        Company andina = company("andina@example.com", "100");
        UUID second = associate(andina, "segundo@example.com", "201", 2);
        UUID first = associate(andina, "primero@example.com", "202", 1);
        UUID disabled = associate(andina, "inactivo@example.com", "203", 3);
        disable(disabled);
        Company other = company("norte@example.com", "300");
        associate(other, "ajeno@example.com", "301", 0);

        find(andina.getAdminUserId())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.technicianUserIds", contains(first.toString(), second.toString())));
    }

    @Test
    void unaCuentaQueNoEsDeUnaEmpresaActivaRespondeListaVacia() throws Exception {
        Company closed = company("cerrada@example.com", "100");
        associate(closed, "tecnico@example.com", "101", 0);
        disable(closed.getAdminUserId());
        UUID withoutCompany = account("sola@example.com", "200", Role.COMPANY).getId();
        UUID technician = account("independiente@example.com", "300", Role.PROFESSIONAL).getId();
        Company empty = company("vacia@example.com", "400");

        for (UUID id : new UUID[]{closed.getAdminUserId(), withoutCompany, technician, UUID.randomUUID(),
                empty.getAdminUserId()}) {
            find(id).andExpect(status().isOk()).andExpect(jsonPath("$.technicianUserIds", empty()));
        }
    }

    // ---------- Validación y seguridad ----------

    @Test
    void sinCuentaOConUnIdentificadorInvalidoResponde400() throws Exception {
        find(null).andExpect(status().isBadRequest());
        find("no-es-un-uuid").andExpect(status().isBadRequest());
    }

    @Test
    void sinLaClaveInternaNoResponde() throws Exception {
        Company andina = company("andina@example.com", "100");
        UUID technician = associate(andina, "tecnico@example.com", "101", 0);
        String body = objectMapper.writeValueAsString(Map.of("companyAdminUserId", andina.getAdminUserId()));

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH).header("X-Internal-Api-Key", "clave-interna-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianUserIds", contains(technician.toString())));
    }
}
