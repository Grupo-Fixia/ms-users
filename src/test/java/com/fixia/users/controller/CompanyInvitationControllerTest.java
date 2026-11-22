package com.fixia.users.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyInvitation;
import com.fixia.users.domain.CompanyTechnician;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.repository.CompanyInvitationRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invitaciones de una empresa a técnicos independientes (GC-523). El acceso es real: la cuenta inicia sesión por la API y
 * el token decide la empresa; ninguna ruta recibe un identificador de empresa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompanyInvitationControllerTest {

    private static final String PASSWORD = "Segura#2026";
    private static final String INVITATIONS = "/api/users/companies/me/invitations";

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
    private CompanyInvitationRepository invitationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Company companyA;
    private Company companyB;
    private UUID carlos;

    @BeforeEach
    void setUp() {
        // El borrado en cascada se lleva técnicos, empresas, asociaciones e invitaciones.
        userRepository.deleteAllInBatch();
        companyA = company("empresa-a@fixia.test", "9100000001", "Servicios Andina");
        companyB = company("empresa-b@fixia.test", "9100000002", "Mantenimientos del Valle");
        carlos = technician("carlos@fixia.test", "9200000001", "Carlos", "Giraldo");
    }

    // ---------- Utilidades ----------

    private User account(String email, String document, Role role, String firstName, String lastName) {
        return userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), role, firstName, lastName,
                DocumentType.CC, document, "3104445566", "v1.0", Instant.now()));
    }

    private Company company(String email, String document, String name) {
        User admin = account(email, document, Role.COMPANY, "Laura", "Gómez");
        return companyRepository.save(new Company(admin.getId(), name, "900100200", Instant.now()));
    }

    private UUID technician(String email, String document, String firstName, String lastName) {
        UUID userId = account(email, document, Role.PROFESSIONAL, firstName, lastName).getId();
        technicianRepository.save(new Technician(userId, Instant.now()));
        return userId;
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, token);
    }

    private MockHttpServletRequestBuilder invite(String email) throws Exception {
        return post(INVITATIONS).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("technicianEmail", email)));
    }

    private String companyAToken() throws Exception {
        return login("empresa-a@fixia.test");
    }

    private String companyBToken() throws Exception {
        return login("empresa-b@fixia.test");
    }

    /** Guarda una invitación ya vencida sin esperar 7 días ni tocar el reloj: creada y vencida en el pasado. */
    private CompanyInvitation expiredInvitation(Company company, UUID technicianUserId) {
        Instant created = Instant.now().minus(Duration.ofDays(10));
        return invitationRepository.save(new CompanyInvitation(company.getId(), technicianUserId, created,
                created.plus(Duration.ofDays(7))));
    }

    private UUID createInvitation(String token, String email) throws Exception {
        String body = mockMvc.perform(as(invite(email), token)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    // ---------- Crear ----------

    @Test
    void laEmpresaInvitaAUnTecnicoIndependiente() throws Exception {
        MvcResult result = mockMvc.perform(as(invite("carlos@fixia.test"), companyAToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.technician.displayName").value("Carlos G."))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.respondedAt").doesNotExist())
                // Nunca se expone el correo, el documento ni el id de la cuenta del técnico.
                .andExpect(content().string(not(containsString("carlos@fixia.test"))))
                .andExpect(content().string(not(containsString("9200000001"))))
                .andExpect(content().string(not(containsString(carlos.toString()))))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        // Vence a los 7 días (regla 6 del PO).
        assertThat(Duration.between(Instant.parse(body.get("createdAt").asText()),
                Instant.parse(body.get("expiresAt").asText()))).isEqualTo(Duration.ofDays(7));

        List<CompanyInvitation> saved = invitationRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getCompanyId()).isEqualTo(companyA.getId());
        assertThat(saved.get(0).getTechnicianUserId()).isEqualTo(carlos);
        // Invitar no vincula: la asociación solo nace cuando el técnico acepte (GC-524).
        assertThat(associationRepository.existsByTechnicianUserId(carlos)).isFalse();
    }

    @Test
    void elCorreoSeNormalizaAntesDeBuscar() throws Exception {
        mockMvc.perform(as(invite("CARLOS@Fixia.Test"), companyAToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.technician.displayName").value("Carlos G."));
    }

    @Test
    void losTresMotivosDeRechazoSeVenIgualParaNoRevelarCuentas() throws Exception {
        String token = companyAToken();
        account("cliente@fixia.test", "9300000001", Role.CLIENT, "Ana", "Ruiz");
        UUID disabled = technician("inactivo@fixia.test", "9200000002", "Inés", "Mora");
        User disabledUser = userRepository.findById(disabled).orElseThrow();
        disabledUser.disable();
        userRepository.save(disabledUser);
        UUID member = technician("asociado@fixia.test", "9200000003", "Julián", "Pardo");
        associationRepository.save(new CompanyTechnician(companyB.getId(), member, Instant.now()));
        account("sinperfil@fixia.test", "9200000004", Role.PROFESSIONAL, "Pedro", "Sin");

        String expected = null;
        for (String email : List.of("noexiste@fixia.test", "cliente@fixia.test", "inactivo@fixia.test",
                "asociado@fixia.test", "sinperfil@fixia.test", "empresa-b@fixia.test")) {
            String body = mockMvc.perform(as(invite(email), token))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.reasons", contains("NOT_INVITABLE")))
                    .andReturn().getResponse().getContentAsString();
            if (expected == null) {
                expected = body;
            }
            assertThat(body).as("respuesta para %s", email).isEqualTo(expected);
        }
        assertThat(invitationRepository.count()).isZero();
    }

    @Test
    void uncorreoVacioOMalFormadoEs400() throws Exception {
        String token = companyAToken();
        for (String body : List.of("{}", "{\"technicianEmail\":\"\"}", "{\"technicianEmail\":\"   \"}",
                "{\"technicianEmail\":\"no-es-un-correo\"}", "no es json")) {
            mockMvc.perform(as(post(INVITATIONS).contentType(MediaType.APPLICATION_JSON).content(body), token))
                    .andExpect(status().isBadRequest());
        }
        assertThat(invitationRepository.count()).isZero();
    }

    @Test
    void unDuplicadoPendienteEs409YNoCreaOtraFila() throws Exception {
        String token = companyAToken();
        createInvitation(token, "carlos@fixia.test");

        mockMvc.perform(as(invite("carlos@fixia.test"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasons", contains("INVITATION_ALREADY_PENDING")));
        assertThat(invitationRepository.count()).isEqualTo(1);
    }

    @Test
    void otraEmpresaPuedeInvitarAlMismoTecnicoSinChocar() throws Exception {
        createInvitation(companyAToken(), "carlos@fixia.test");
        createInvitation(companyBToken(), "carlos@fixia.test");
        assertThat(invitationRepository.count()).isEqualTo(2);
    }

    @Test
    void despuesDeCancelarSePuedeVolverAInvitar() throws Exception {
        String token = companyAToken();
        UUID first = createInvitation(token, "carlos@fixia.test");
        mockMvc.perform(as(delete(INVITATIONS + "/" + first), token)).andExpect(status().isNoContent());

        UUID second = createInvitation(token, "carlos@fixia.test");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void unaInvitacionVencidaNoImpideReinvitarYSeVeVencida() throws Exception {
        String token = companyAToken();
        CompanyInvitation stale = expiredInvitation(companyA, carlos);

        mockMvc.perform(as(invite("carlos@fixia.test"), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(as(get(INVITATIONS).param("status", "EXPIRED"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations", hasSize(1)))
                .andExpect(jsonPath("$.invitations[0].id").value(stale.getId().toString()));
    }

    @Test
    void dosInvitacionesSimultaneasAlMismoTecnicoDejanUnaSolaPendiente() throws Exception {
        String token = companyAToken();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> {
                start.await();
                return mockMvc.perform(as(invite("carlos@fixia.test"), token)).andReturn().getResponse().getStatus();
            };
            Future<Integer> first = pool.submit(attempt);
            Future<Integer> second = pool.submit(attempt);
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(invitationRepository.count()).isEqualTo(1);
    }

    // ---------- Listar ----------

    @Test
    void elListadoVaDeLaMasRecienteALaMasAntiguaYSoloMuestraLasDeLaEmpresa() throws Exception {
        UUID mora = technician("mora@fixia.test", "9200000005", "Inés", "Mora");
        String token = companyAToken();
        UUID first = createInvitation(token, "carlos@fixia.test");
        UUID second = createInvitation(token, "mora@fixia.test");
        createInvitation(companyBToken(), "carlos@fixia.test");

        mockMvc.perform(as(get(INVITATIONS), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations", hasSize(2)))
                .andExpect(jsonPath("$.invitations[0].id").value(second.toString()))
                .andExpect(jsonPath("$.invitations[0].technician.displayName").value("Inés M."))
                .andExpect(jsonPath("$.invitations[1].id").value(first.toString()))
                .andExpect(jsonPath("$.invitations[1].technician.displayName").value("Carlos G."));
        assertThat(mora).isNotNull();
    }

    @Test
    void unaEmpresaSinInvitacionesRecibeListaVacia() throws Exception {
        mockMvc.perform(as(get(INVITATIONS), companyAToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations", hasSize(0)));
    }

    @Test
    void elFiltroPorEstadoUsaElEstadoQueSeMuestra() throws Exception {
        UUID mora = technician("mora@fixia.test", "9200000005", "Inés", "Mora");
        UUID pardo = technician("pardo@fixia.test", "9200000006", "Julián", "Pardo");
        String token = companyAToken();
        UUID pending = createInvitation(token, "carlos@fixia.test");
        UUID cancelled = createInvitation(token, "mora@fixia.test");
        mockMvc.perform(as(delete(INVITATIONS + "/" + cancelled), token)).andExpect(status().isNoContent());
        CompanyInvitation expired = expiredInvitation(companyA, pardo);

        mockMvc.perform(as(get(INVITATIONS).param("status", "PENDING"), token))
                .andExpect(jsonPath("$.invitations", hasSize(1)))
                .andExpect(jsonPath("$.invitations[0].id").value(pending.toString()));
        mockMvc.perform(as(get(INVITATIONS).param("status", "CANCELLED"), token))
                .andExpect(jsonPath("$.invitations", hasSize(1)))
                .andExpect(jsonPath("$.invitations[0].id").value(cancelled.toString()))
                .andExpect(jsonPath("$.invitations[0].respondedAt").isNotEmpty());
        mockMvc.perform(as(get(INVITATIONS).param("status", "EXPIRED"), token))
                .andExpect(jsonPath("$.invitations", hasSize(1)))
                .andExpect(jsonPath("$.invitations[0].id").value(expired.getId().toString()))
                .andExpect(jsonPath("$.invitations[0].status").value("EXPIRED"));
        mockMvc.perform(as(get(INVITATIONS).param("status", "ACCEPTED"), token))
                .andExpect(jsonPath("$.invitations", hasSize(0)));
        assertThat(mora).isNotNull();
    }

    @Test
    void unEstadoDeFiltroDesconocidoEs400() throws Exception {
        mockMvc.perform(as(get(INVITATIONS).param("status", "CUALQUIERA"), companyAToken()))
                .andExpect(status().isBadRequest());
    }

    // ---------- Cancelar ----------

    @Test
    void laEmpresaCancelaUnaPendienteYUnaSegundaVezEs409() throws Exception {
        String token = companyAToken();
        UUID id = createInvitation(token, "carlos@fixia.test");

        mockMvc.perform(as(delete(INVITATIONS + "/" + id), token)).andExpect(status().isNoContent());
        mockMvc.perform(as(get(INVITATIONS), token))
                .andExpect(jsonPath("$.invitations[0].status").value("CANCELLED"));
        mockMvc.perform(as(delete(INVITATIONS + "/" + id), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasons", contains("INVITATION_NOT_PENDING")));
    }

    @Test
    void unaVencidaYaNoSePuedeCancelar() throws Exception {
        CompanyInvitation stale = expiredInvitation(companyA, carlos);
        mockMvc.perform(as(delete(INVITATIONS + "/" + stale.getId()), companyAToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void unaInvitacionAjenaOInexistenteEs404YNoSeToca() throws Exception {
        UUID ofA = createInvitation(companyAToken(), "carlos@fixia.test");

        mockMvc.perform(as(delete(INVITATIONS + "/" + ofA), companyBToken())).andExpect(status().isNotFound());
        mockMvc.perform(as(delete(INVITATIONS + "/" + UUID.randomUUID()), companyBToken()))
                .andExpect(status().isNotFound());
        // La invitación de A sigue pendiente.
        mockMvc.perform(as(get(INVITATIONS).param("status", "PENDING"), companyAToken()))
                .andExpect(jsonPath("$.invitations", hasSize(1)));
    }

    @Test
    void unIdQueNoEsUuidEs400() throws Exception {
        mockMvc.perform(as(delete(INVITATIONS + "/no-es-uuid"), companyAToken()))
                .andExpect(status().isBadRequest());
    }

    // ---------- Acceso ----------

    @Test
    void sinTokenEs401() throws Exception {
        mockMvc.perform(invite("carlos@fixia.test")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(INVITATIONS)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(INVITATIONS + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void ElTecnicoYElClienteNoPuedenInvitarNiVerInvitaciones() throws Exception {
        account("cliente@fixia.test", "9300000001", Role.CLIENT, "Ana", "Ruiz");
        for (String email : List.of("carlos@fixia.test", "cliente@fixia.test")) {
            String token = login(email);
            mockMvc.perform(as(invite("mora@fixia.test"), token)).andExpect(status().isForbidden());
            mockMvc.perform(as(get(INVITATIONS), token)).andExpect(status().isForbidden());
            mockMvc.perform(as(delete(INVITATIONS + "/" + UUID.randomUUID()), token)).andExpect(status().isForbidden());
        }
        assertThat(invitationRepository.count()).isZero();
    }

    @Test
    void unaCuentaDeEmpresaSinEmpresaCargadaEs404() throws Exception {
        account("huerfana@fixia.test", "9100000009", Role.COMPANY, "Rosa", "Sola");
        mockMvc.perform(as(invite("carlos@fixia.test"), login("huerfana@fixia.test")))
                .andExpect(status().isNotFound());
    }
}
