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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta interna «¿este técnico es de esta empresa?» (GC-466, para GC-468). Todos los casos de «no» responden 200 con
 * {@code associated: false}: quien consulta no aprende si una cuenta existe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalCompanyControllerTest {

    private static final String PATH = "/internal/companies/technician-association";

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

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
    }

    // ---------- Utilidades ----------

    private User account(String email, String document, Role role) {
        return userRepository.save(new User(email, "hash", role, "Laura", "Gómez", DocumentType.CC, document,
                "3104445566", "v1.0", Instant.now()));
    }

    private Company company(String email, String document) {
        User admin = account(email, document, Role.COMPANY);
        return companyRepository.save(new Company(admin.getId(), "Empresa " + document, null, Instant.now()));
    }

    private UUID associatedTechnician(Company company, String email, String document) {
        UUID technician = account(email, document, Role.PROFESSIONAL).getId();
        technicianRepository.save(new Technician(technician, Instant.now()));
        associationRepository.save(new CompanyTechnician(company.getId(), technician, Instant.now()));
        return technician;
    }

    private ResultActions ask(UUID adminUserId, UUID technicianUserId) throws Exception {
        return mockMvc.perform(post(PATH).with(InternalTechnicianControllerTest.internalService())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "companyAdminUserId", adminUserId, "technicianUserId", technicianUserId))));
    }

    // ---------- Respuestas ----------

    @Test
    void confirmaAlTecnicoAsociadoALaEmpresa() throws Exception {
        Company company = company("andina@example.com", "900100");
        UUID technician = associatedTechnician(company, "carlos@example.com", "2001");

        ask(company.getAdminUserId(), technician)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.associated").value(true));
    }

    @Test
    void rechazaAlTecnicoDeOtraEmpresa() throws Exception {
        Company andina = company("andina@example.com", "900100");
        Company valle = company("valle@example.com", "900200");
        UUID ofValle = associatedTechnician(valle, "julian@example.com", "2002");

        ask(andina.getAdminUserId(), ofValle).andExpect(status().isOk()).andExpect(jsonPath("$.associated").value(false));
    }

    @Test
    void rechazaAUnTecnicoIndependienteSinAsociacion() throws Exception {
        Company company = company("andina@example.com", "900100");
        UUID free = account("libre@example.com", "2003", Role.PROFESSIONAL).getId();
        technicianRepository.save(new Technician(free, Instant.now()));

        ask(company.getAdminUserId(), free).andExpect(jsonPath("$.associated").value(false));
    }

    @Test
    void rechazaCuentasInexistentesOQueNoSonDeEmpresa() throws Exception {
        Company company = company("andina@example.com", "900100");
        UUID technician = associatedTechnician(company, "carlos@example.com", "2001");
        UUID client = account("cliente@example.com", "3001", Role.CLIENT).getId();

        ask(UUID.randomUUID(), technician).andExpect(status().isOk()).andExpect(jsonPath("$.associated").value(false));
        ask(client, technician).andExpect(status().isOk()).andExpect(jsonPath("$.associated").value(false));
        // El técnico no puede hacerse pasar por el administrador de la empresa.
        ask(technician, technician).andExpect(status().isOk()).andExpect(jsonPath("$.associated").value(false));
        ask(company.getAdminUserId(), UUID.randomUUID()).andExpect(jsonPath("$.associated").value(false));
    }

    @Test
    void rechazaSiLaCuentaDelAdministradorOLaDelTecnicoEstanDeshabilitadas() throws Exception {
        Company company = company("andina@example.com", "900100");
        UUID technician = associatedTechnician(company, "carlos@example.com", "2001");

        User technicianUser = userRepository.findById(technician).orElseThrow();
        technicianUser.disable();
        userRepository.save(technicianUser);
        ask(company.getAdminUserId(), technician).andExpect(jsonPath("$.associated").value(false));

        // User no tiene cómo reactivarse: la deshabilitación del administrador se prueba con otro técnico asociado.
        UUID other = associatedTechnician(company, "diana@example.com", "2004");
        ask(company.getAdminUserId(), other).andExpect(jsonPath("$.associated").value(true));
        User admin = userRepository.findById(company.getAdminUserId()).orElseThrow();
        admin.disable();
        userRepository.save(admin);
        ask(company.getAdminUserId(), other).andExpect(jsonPath("$.associated").value(false));
    }

    @Test
    void exigeLosDosIdentificadores() throws Exception {
        mockMvc.perform(post(PATH).with(InternalTechnicianControllerTest.internalService())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH).with(InternalTechnicianControllerTest.internalService())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Autenticación entre servicios ----------

    @Test
    void sinClaveInternaSeRechaza() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyAdminUserId\":\"" + UUID.randomUUID()
                                + "\",\"technicianUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conLaClaveInternaRealResponde() throws Exception {
        Company company = company("andina@example.com", "900100");
        UUID technician = associatedTechnician(company, "carlos@example.com", "2001");

        mockMvc.perform(post(PATH).header("X-Internal-Api-Key", "clave-interna-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyAdminUserId", company.getAdminUserId(), "technicianUserId", technician))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associated").value(true));
    }
}
