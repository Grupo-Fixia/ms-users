package com.fixia.users.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.repository.CompanyRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Consultas del ADMIN en ms-users (GC-535, contrato docs/contracts/admin.md §2, §3 y §5). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    private static final String PASSWORD = "Clave1234";
    private static final String USERS = "/api/users/admin/users";
    private static final String AUDIT = "/api/users/admin/audit-events";

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
    private AdminAuditEventRepository auditRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        auditRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch(); // en cascada: técnicos, empresas y asociaciones
        admin = account("admin@fixia.test", "9000000001", Role.ADMIN, "Ada", "Admin");
        adminToken = login("admin@fixia.test");
    }

    // ---------- Acceso (§2) ----------

    @Test
    void sinTokenEs401() throws Exception {
        mockMvc.perform(get(USERS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(AUDIT)).andExpect(status().isUnauthorized());
    }

    @Test
    void clienteTecnicoYEmpresaReciben403YNoQuedaRegistro() throws Exception {
        account("cli@fixia.test", "1000000001", Role.CLIENT, "Carla", "Cliente");
        account("tec@fixia.test", "1000000002", Role.PROFESSIONAL, "Tomás", "Técnico");
        account("emp@fixia.test", "1000000003", Role.COMPANY, "Elena", "Empresa");
        for (String email : List.of("cli@fixia.test", "tec@fixia.test", "emp@fixia.test")) {
            String token = login(email);
            mockMvc.perform(as(get(USERS), token)).andExpect(status().isForbidden());
            mockMvc.perform(as(get(USERS + "/" + admin.getId()), token)).andExpect(status().isForbidden());
            mockMvc.perform(as(get(AUDIT), token)).andExpect(status().isForbidden());
        }
        assertThat(auditRepository.count()).isZero();
    }

    // ---------- Lista de usuarios (§3.1) ----------

    @Test
    void listaUsuariosSinDocumentoNiTelefonoYRegistraLaConsulta() throws Exception {
        account("cli@fixia.test", "1000000001", Role.CLIENT, "Carla", "Cliente");

        mockMvc.perform(as(get(USERS), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].email", containsInAnyOrder("admin@fixia.test", "cli@fixia.test")))
                .andExpect(jsonPath("$.items[0].role").exists())
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].documentNumber").doesNotExist())
                .andExpect(jsonPath("$.items[0].phone").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        List<AdminAuditEvent> events = auditRepository.findAll();
        assertThat(events).hasSize(1);
        AdminAuditEvent event = events.get(0);
        assertThat(event.getAdminId()).isEqualTo(admin.getId());
        assertThat(event.getAction()).isEqualTo(AdminAction.USER_LIST);
        assertThat(event.getTargetType()).isNull();
        assertThat(event.getOutcome()).isEqualTo(AdminAuditOutcome.OK);
        assertThat(event.getDetails()).isEqualTo("{\"limit\":20}");
    }

    @Test
    void filtraPorRolEstadoYTexto() throws Exception {
        account("carla@fixia.test", "1000000001", Role.CLIENT, "Carla", "Gómez");
        account("carlos@fixia.test", "1000000002", Role.PROFESSIONAL, "Carlos", "Ruiz");
        User disabled = account("luis@fixia.test", "1000000003", Role.CLIENT, "Luis", "Pardo");
        disabled.disable();
        userRepository.save(disabled);

        mockMvc.perform(as(get(USERS).param("role", "client"), adminToken))
                .andExpect(jsonPath("$.items[*].email", containsInAnyOrder("carla@fixia.test", "luis@fixia.test")));
        mockMvc.perform(as(get(USERS).param("status", "DISABLED"), adminToken))
                .andExpect(jsonPath("$.items[*].email", containsInAnyOrder("luis@fixia.test")));
        // Busca en correo, nombre y apellido, sin distinguir mayúsculas.
        mockMvc.perform(as(get(USERS).param("q", "CARL"), adminToken))
                .andExpect(jsonPath("$.items[*].email", containsInAnyOrder("carla@fixia.test", "carlos@fixia.test")));
        mockMvc.perform(as(get(USERS).param("q", "gómez"), adminToken))
                .andExpect(jsonPath("$.items[*].email", containsInAnyOrder("carla@fixia.test")));
        mockMvc.perform(as(get(USERS).param("role", "CLIENT").param("q", "carl"), adminToken))
                .andExpect(jsonPath("$.items[*].email", containsInAnyOrder("carla@fixia.test")));
        // Los comodines de LIKE se buscan como texto.
        mockMvc.perform(as(get(USERS).param("q", "%_"), adminToken))
                .andExpect(jsonPath("$.items", hasSize(0)));

        assertThat(auditRepository.findAll()).extracting(AdminAuditEvent::getDetails)
                .contains("{\"role\":\"CLIENT\",\"q\":\"carl\",\"limit\":20}");
    }

    @Test
    void paginaPorCursorSinRepetirNiSaltarUsuarios() throws Exception {
        List<String> expected = new ArrayList<>(List.of("admin@fixia.test"));
        for (int i = 0; i < 6; i++) {
            expected.add(account("u" + i + "@fixia.test", "200000000" + i, Role.CLIENT, "Usuario", "N" + i).getEmail());
        }

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            var request = get(USERS).param("limit", "3");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode page = json(mockMvc.perform(as(request, adminToken)).andExpect(status().isOk()));
            page.get("items").forEach(item -> seen.add(item.get("email").asText()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).hasSize(7).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void parametrosInvalidosResponden400() throws Exception {
        mockMvc.perform(as(get(USERS).param("role", "ROOT"), adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El valor de role no es válido"));
        mockMvc.perform(as(get(USERS).param("status", "BLOCKED"), adminToken)).andExpect(status().isBadRequest());
        mockMvc.perform(as(get(USERS).param("q", "a"), adminToken)).andExpect(status().isBadRequest());
        mockMvc.perform(as(get(USERS).param("q", "x".repeat(101)), adminToken)).andExpect(status().isBadRequest());
        mockMvc.perform(as(get(USERS).param("limit", "0"), adminToken)).andExpect(status().isBadRequest());
        mockMvc.perform(as(get(USERS).param("limit", "101"), adminToken)).andExpect(status().isBadRequest());
        mockMvc.perform(as(get(USERS).param("cursor", "no-es-un-cursor"), adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El cursor no es válido"));
        mockMvc.perform(as(get(USERS + "/no-es-uuid"), adminToken)).andExpect(status().isBadRequest());
        mockMvc.perform(as(get(AUDIT).param("action", "DELETE_ALL"), adminToken)).andExpect(status().isBadRequest());
        assertThat(auditRepository.count()).isZero();
    }

    // ---------- Detalle (§3.2) ----------

    @Test
    void detalleConDocumentoEnmascaradoYDatosDelRol() throws Exception {
        User technician = account("tec@fixia.test", "1020304050", Role.PROFESSIONAL, "Tomás", "Técnico");
        technicianRepository.save(new Technician(technician.getId(), Instant.now()));
        User company = account("emp@fixia.test", "9001002003", Role.COMPANY, "Elena", "Empresa");
        companyRepository.save(new Company(company.getId(), "Servicios Andina", "900100200", Instant.now()));

        mockMvc.perform(as(get(USERS + "/" + technician.getId()), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("tec@fixia.test"))
                .andExpect(jsonPath("$.phone").value("3104445566"))
                .andExpect(jsonPath("$.documentType").value("CC"))
                .andExpect(jsonPath("$.documentNumberMasked").value("******4050"))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.companyName").doesNotExist())
                .andExpect(jsonPath("$.documentNumber").doesNotExist())
                .andExpect(content().string(not(containsString("1020304050"))));

        mockMvc.perform(as(get(USERS + "/" + company.getId()), adminToken))
                .andExpect(jsonPath("$.companyName").value("Servicios Andina"))
                .andExpect(jsonPath("$.verificationStatus").doesNotExist());

        assertThat(auditRepository.findAll())
                .extracting(AdminAuditEvent::getAction, AdminAuditEvent::getTargetType, AdminAuditEvent::getTargetId,
                        AdminAuditEvent::getOutcome)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(AdminAction.USER_VIEW, "USER", technician.getId(),
                                AdminAuditOutcome.OK),
                        org.assertj.core.groups.Tuple.tuple(AdminAction.USER_VIEW, "USER", company.getId(),
                                AdminAuditOutcome.OK));
    }

    @Test
    void usuarioInexistenteEs404YQuedaRegistradoComoNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        mockMvc.perform(as(get(USERS + "/" + missing), adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Usuario no encontrado"));

        List<AdminAuditEvent> events = auditRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTargetId()).isEqualTo(missing);
        assertThat(events.get(0).getOutcome()).isEqualTo(AdminAuditOutcome.NOT_FOUND);
    }

    @Test
    void enmascaraDocumentosCortos() {
        assertThat(AdminUserService.mask("1234")).isEqualTo("****");
        assertThat(AdminUserService.mask("12345")).isEqualTo("*2345");
        assertThat(AdminUserService.mask("")).isEmpty();
        assertThat(AdminUserService.mask(null)).isNull();
    }

    // ---------- Registro de acciones (§3.3 y §5) ----------

    @Test
    void elRegistroSeConsultaDelMasRecienteAlMasAntiguoYNoSeIncluyeASiMismo() throws Exception {
        mockMvc.perform(as(get(USERS), adminToken)).andExpect(status().isOk());
        mockMvc.perform(as(get(USERS + "/" + admin.getId()), adminToken)).andExpect(status().isOk());

        mockMvc.perform(as(get(AUDIT), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].action").value("USER_VIEW"))
                .andExpect(jsonPath("$.items[0].targetType").value("USER"))
                .andExpect(jsonPath("$.items[0].adminId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.items[1].action").value("USER_LIST"))
                .andExpect(jsonPath("$.items[1].details.limit").value(20));

        mockMvc.perform(as(get(AUDIT).param("action", "user_list"), adminToken))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mockMvc.perform(as(get(AUDIT).param("adminId", UUID.randomUUID().toString()), adminToken))
                .andExpect(jsonPath("$.items", hasSize(0)));

        assertThat(auditRepository.findAll()).extracting(AdminAuditEvent::getAction)
                .containsOnly(AdminAction.USER_LIST, AdminAction.USER_VIEW, AdminAction.AUDIT_LIST)
                .filteredOn(action -> action == AdminAction.AUDIT_LIST).hasSize(3);
    }

    @Test
    void elAdminNoUsaLasRutasDeOtrosRoles() throws Exception {
        mockMvc.perform(as(get("/api/users/companies/me"), adminToken)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/users/technicians/me/profile"), adminToken)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/users/clients/me/profile"), adminToken)).andExpect(status().isForbidden());
    }

    // ---------- Utilidades ----------

    private User account(String email, String document, Role role, String firstName, String lastName) {
        return userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), role, firstName, lastName,
                DocumentType.CC, document, "3104445566", "v1.0", Instant.now()));
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
