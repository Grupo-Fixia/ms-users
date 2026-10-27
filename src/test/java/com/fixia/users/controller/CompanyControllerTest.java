package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyTechnician;
import com.fixia.users.domain.CoverageZone;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.ServiceCategory;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Identidad de empresa y técnicos asociados (GC-466). El acceso es real: la cuenta inicia sesión por la API y el token
 * decide la empresa; ninguna ruta recibe un identificador de empresa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompanyControllerTest {

    private static final String PASSWORD = "Segura#2026";
    private static final String ME = "/api/users/companies/me";
    private static final String TECHNICIANS = ME + "/technicians";

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        // El borrado en cascada se lleva técnicos, empresas y asociaciones.
        userRepository.deleteAllInBatch();
    }

    // ---------- Utilidades ----------

    private User account(String email, String document, Role role, String firstName, String lastName) {
        return userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), role, firstName, lastName,
                DocumentType.CC, document, "3104445566", "v1.0", Instant.now()));
    }

    /** Cuenta COMPANY con su empresa; devuelve la empresa. */
    private Company company(String email, String document, String name) {
        User admin = account(email, document, Role.COMPANY, "Laura", "Gómez");
        return companyRepository.save(new Company(admin.getId(), name, "900100200", Instant.now()));
    }

    /** Técnico con perfil y una especialidad; devuelve el id de su cuenta. */
    private UUID technician(String email, String document, String firstName, String lastName) {
        UUID userId = account(email, document, Role.PROFESSIONAL, firstName, lastName).getId();
        technicianRepository.save(new Technician(userId, Instant.now()));
        transactionTemplate.executeWithoutResult(status -> {
            Technician technician = technicianRepository.findByUserId(userId).orElseThrow();
            technician.updateProfile("Plomero con experiencia en redes de agua", (short) 8,
                    Set.of(ServiceCategory.PLUMBING), Instant.now());
            technician.updateCoverageZone(new CoverageZone("Cali", 3.45, -76.53, 10), Instant.now());
        });
        return userId;
    }

    /** Asocia en orden: cada llamada usa un instante posterior para que el orden del listado sea determinista. */
    private int tick = 0;

    private void associate(Company company, UUID technicianUserId) {
        associationRepository.save(new CompanyTechnician(company.getId(), technicianUserId,
                Instant.parse("2026-10-01T10:00:00Z").plusSeconds(tick++)));
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }

    // ---------- Acceso y roles ----------

    @Test
    void laEmpresaIniciaSesionConLaCuentaExistenteYConsultaSuEmpresa() throws Exception {
        company("empresa@example.com", "900100", "Servicios Andina");

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, login("empresa@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Servicios Andina"))
                .andExpect(jsonPath("$.nit").value("900100200"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void sinTokenSeRechazaConUnAutenticado() throws Exception {
        mockMvc.perform(get(ME)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(TECHNICIANS)).andExpect(status().isUnauthorized());
    }

    @Test
    void clienteYTecnicoNoPuedenUsarLasRutasDeEmpresa() throws Exception {
        account("cliente@example.com", "1001", Role.CLIENT, "Ana", "Ruiz");
        technician("tecnico@example.com", "1002", "Carlos", "Gómez");

        for (String email : new String[]{"cliente@example.com", "tecnico@example.com"}) {
            String token = login(email);
            mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
            mockMvc.perform(get(TECHNICIANS).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void laCuentaDeEmpresaNoPuedeUsarLasRutasDeClienteNiDeTecnico() throws Exception {
        company("empresa@example.com", "900100", "Servicios Andina");
        String token = login("empresa@example.com");

        mockMvc.perform(get("/api/users/clients/me/profile").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/technicians/me/profile").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaCuentaDeEmpresaSinEmpresaCargadaRecibe404() throws Exception {
        account("sinempresa@example.com", "900101", Role.COMPANY, "Laura", "Gómez");
        String token = login("sinempresa@example.com");

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Empresa no encontrada"));
        mockMvc.perform(get(TECHNICIANS).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void unaCuentaDeshabilitadaDespuesDeIniciarSesionDejaDeTenerAcceso() throws Exception {
        Company company = company("empresa@example.com", "900100", "Servicios Andina");
        String token = login("empresa@example.com");

        User admin = userRepository.findById(company.getAdminUserId()).orElseThrow();
        admin.disable();
        userRepository.save(admin);

        mockMvc.perform(get(TECHNICIANS).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Listado ----------

    @Test
    void listaSoloLosTecnicosDeLaEmpresaAutenticadaEnOrdenDeAlta() throws Exception {
        Company andina = company("andina@example.com", "900100", "Servicios Andina");
        Company valle = company("valle@example.com", "900200", "Mantenimientos del Valle");
        associate(andina, technician("carlos@example.com", "2001", "Carlos", "Giraldo"));
        associate(valle, technician("julian@example.com", "2002", "Julián", "Pardo"));
        associate(andina, technician("diana@example.com", "2003", "Diana", "Torres"));

        mockMvc.perform(get(TECHNICIANS).header(HttpHeaders.AUTHORIZATION, login("andina@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians", hasSize(2)))
                .andExpect(jsonPath("$.technicians[*].displayName", contains("Carlos G.", "Diana T.")))
                .andExpect(jsonPath("$.technicians[0].categories", contains("PLUMBING")))
                .andExpect(jsonPath("$.technicians[0].city").value("Cali"));

        mockMvc.perform(get(TECHNICIANS).header(HttpHeaders.AUTHORIZATION, login("valle@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians[*].displayName", contains("Julián P.")));
    }

    @Test
    void elIdDelTecnicoEsElDeSuCuentaParaQuePuedaPostularse() throws Exception {
        Company company = company("andina@example.com", "900100", "Servicios Andina");
        UUID technician = technician("carlos@example.com", "2001", "Carlos", "Giraldo");
        associate(company, technician);

        mockMvc.perform(get(TECHNICIANS).header(HttpHeaders.AUTHORIZATION, login("andina@example.com")))
                .andExpect(jsonPath("$.technicians[0].userId").value(technician.toString()));
    }

    @Test
    void unaEmpresaSinTecnicosRecibeListaVacia() throws Exception {
        company("andina@example.com", "900100", "Servicios Andina");

        mockMvc.perform(get(TECHNICIANS).header(HttpHeaders.AUTHORIZATION, login("andina@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians", hasSize(0)));
    }

    @Test
    void lasAsociacionesSeRecuperanAlVolverAIniciarSesion() throws Exception {
        Company company = company("andina@example.com", "900100", "Servicios Andina");
        associate(company, technician("carlos@example.com", "2001", "Carlos", "Giraldo"));

        for (int session = 0; session < 2; session++) {
            mockMvc.perform(get(TECHNICIANS).header(HttpHeaders.AUTHORIZATION, login("andina@example.com")))
                    .andExpect(jsonPath("$.technicians", hasSize(1)));
        }
    }

    @Test
    void noExponeDatosPrivadosDelTecnico() throws Exception {
        Company company = company("andina@example.com", "900100", "Servicios Andina");
        associate(company, technician("carlos@example.com", "2001", "Carlos", "Giraldo"));

        String body = mockMvc.perform(get(TECHNICIANS).header(HttpHeaders.AUTHORIZATION, login("andina@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("carlos@example.com", "3104445566", "2001", "3.45", "-76.53", "Giraldo");
    }

    @Test
    void omiteAlTecnicoConCuentaDeshabilitadaYLasRelacionesInconsistentes() throws Exception {
        Company company = company("andina@example.com", "900100", "Servicios Andina");
        UUID active = technician("carlos@example.com", "2001", "Carlos", "Giraldo");
        UUID disabled = technician("diana@example.com", "2003", "Diana", "Torres");
        // Inconsistente: tiene perfil de técnico pero su cuenta es de cliente.
        UUID notProfessional = account("ana@example.com", "2004", Role.CLIENT, "Ana", "Ruiz").getId();
        technicianRepository.save(new Technician(notProfessional, Instant.now()));
        associate(company, active);
        associate(company, disabled);
        associate(company, notProfessional);

        User disabledUser = userRepository.findById(disabled).orElseThrow();
        disabledUser.disable();
        userRepository.save(disabledUser);

        mockMvc.perform(get(TECHNICIANS).header(HttpHeaders.AUTHORIZATION, login("andina@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians", hasSize(1)))
                .andExpect(jsonPath("$.technicians[0].userId").value(active.toString()));
    }

    @Test
    void unIdentificadorDeEmpresaEnLaPeticionNoPermiteLeerOtraEmpresa() throws Exception {
        company("andina@example.com", "900100", "Servicios Andina");
        Company valle = company("valle@example.com", "900200", "Mantenimientos del Valle");
        associate(valle, technician("julian@example.com", "2002", "Julián", "Pardo"));
        String token = login("andina@example.com");

        // La empresa sale siempre del token: ni un parámetro ni un segmento de ruta con el id ajeno cambian el resultado.
        mockMvc.perform(get(TECHNICIANS).param("companyId", valle.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians", hasSize(0)));
        mockMvc.perform(get(ME + "/" + valle.getId() + "/technicians").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }
}
