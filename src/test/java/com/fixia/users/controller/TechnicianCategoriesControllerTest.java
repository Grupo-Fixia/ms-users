package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TechnicianCategoriesControllerTest {

    private static final String CATALOG = "/api/users/service-categories";
    private static final String CATEGORIES = "/api/users/technicians/me/categories";
    private static final String PROFILE = "/api/users/technicians/me/profile";
    private static final String PASSWORD = "Tecnico123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // DELETE inmediato en la base; el borrado en cascada se lleva técnicos, categorías y refresh tokens.
        userRepository.deleteAllInBatch();
    }

    // ---------- Utilidades ----------

    private Map<String, Object> account(String email, String documentNumber) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Carlos");
        body.put("lastName", "Gómez");
        body.put("documentType", "CC");
        body.put("documentNumber", documentNumber);
        body.put("email", email);
        body.put("phone", "+573104445566");
        body.put("password", PASSWORD);
        body.put("policyVersion", "v1.0");
        body.put("consentAccepted", true);
        return body;
    }

    private UUID register(String path, String email, String documentNumber) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(account(email, documentNumber))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID registerTechnician(String email, String documentNumber) throws Exception {
        return register("/api/users/technicians", email, documentNumber);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private ResultActions putJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(put(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions updateCategories(String token, List<String> categories) throws Exception {
        return putJson(CATEGORIES, token, Map.of("categories", categories));
    }

    private ResultActions getWithToken(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Map<String, Object> profile(List<String> categories) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("professionalDescription", "Plomero con experiencia en redes hidráulicas residenciales.");
        body.put("yearsOfExperience", 8);
        body.put("categories", categories);
        return body;
    }

    private int storedCategoryRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM technician_categories", Integer.class);
    }

    /** Lanza todas las peticiones a la vez y devuelve el código HTTP de cada una. */
    private List<Integer> runAtOnce(List<Callable<ResultActions>> requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> pending = new ArrayList<>();
            for (Callable<ResultActions> request : requests) {
                pending.add(pool.submit(() -> {
                    start.await();
                    return request.call().andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : pending) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- Catálogo habilitado ----------

    @Test
    void elCatalogoTraeTodasLasCategoriasConSuNombre() throws Exception {
        registerTechnician("carlos@example.com", "79800900");
        String[] codes = Arrays.stream(ServiceCategory.values()).map(Enum::name).toArray(String[]::new);

        getWithToken(CATALOG, login("carlos@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains(codes)))
                .andExpect(jsonPath("$[0].code").value("PLUMBING"))
                .andExpect(jsonPath("$[0].name").value("Plomería"));
    }

    @Test
    void unClienteTambienPuedeConsultarElCatalogoPeroNoSinToken() throws Exception {
        register("/api/users/clients", "cliente@example.com", "1020304050");

        getWithToken(CATALOG, login("cliente@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(ServiceCategory.values().length));
        mockMvc.perform(get(CATALOG)).andExpect(status().isUnauthorized());
    }

    // ---------- Consultar y actualizar ----------

    @Test
    void elTecnicoRecienRegistradoNoTieneEspecialidades() throws Exception {
        registerTechnician("carlos@example.com", "79800900");

        getWithToken(CATEGORIES, login("carlos@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void elTecnicoSeleccionaSusEspecialidadesSinHaberDiligenciadoElPerfil() throws Exception {
        UUID userId = registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");

        updateCategories(token, List.of("PLUMBING", "ELECTRICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", containsInAnyOrder("PLUMBING", "ELECTRICAL")));

        getWithToken(CATEGORIES, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", containsInAnyOrder("PLUMBING", "ELECTRICAL")));
        Technician saved = technicianRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getCategories()).containsExactlyInAnyOrder(ServiceCategory.PLUMBING, ServiceCategory.ELECTRICAL);
        assertThat(saved.getProfessionalDescription()).isNull();
    }

    @Test
    void alActualizarLasEspecialidadesNoCambiaElRestoDelPerfil() throws Exception {
        registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");
        putJson(PROFILE, token, profile(List.of("PLUMBING", "MAINTENANCE"))).andExpect(status().isOk());

        updateCategories(token, List.of("PLUMBING", "CARPENTRY")).andExpect(status().isOk());

        getWithToken(PROFILE, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalDescription")
                        .value("Plomero con experiencia en redes hidráulicas residenciales."))
                .andExpect(jsonPath("$.yearsOfExperience").value(8))
                .andExpect(jsonPath("$.categories", containsInAnyOrder("PLUMBING", "CARPENTRY")));
        assertThat(storedCategoryRows()).isEqualTo(2);
    }

    @Test
    void conUnaListaVaciaElTecnicoRetiraTodasSusEspecialidades() throws Exception {
        registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");
        updateCategories(token, List.of("PLUMBING", "ELECTRICAL")).andExpect(status().isOk());

        updateCategories(token, List.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isEmpty());

        assertThat(storedCategoryRows()).isZero();
    }

    @Test
    void soloAceptaValoresDelCatalogo() throws Exception {
        UUID userId = registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");
        updateCategories(token, List.of("PLUMBING")).andExpect(status().isOk());

        updateCategories(token, List.of("PLUMBING", "JARDINERIA")).andExpect(status().isBadRequest());
        putJson(CATEGORIES, token, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("categories")));
        putJson(CATEGORIES, token, Map.of("categories", Arrays.asList("ELECTRICAL", null)))
                .andExpect(status().isBadRequest());

        assertThat(technicianRepository.findByUserId(userId).orElseThrow().getCategories())
                .containsExactly(ServiceCategory.PLUMBING);
    }

    // ---------- Autorización ----------

    @Test
    void cadaTecnicoSoloVeYModificaSusPropiasEspecialidades() throws Exception {
        UUID carlos = registerTechnician("carlos@example.com", "79800900");
        UUID laura = registerTechnician("laura@example.com", "52600700");

        updateCategories(login("laura@example.com"), List.of("PAINTING")).andExpect(status().isOk());

        getWithToken(CATEGORIES, login("carlos@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isEmpty());
        assertThat(technicianRepository.findByUserId(carlos).orElseThrow().getCategories()).isEmpty();
        assertThat(technicianRepository.findByUserId(laura).orElseThrow().getCategories())
                .containsExactly(ServiceCategory.PAINTING);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(CATEGORIES)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(CATEGORIES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("categories", List.of("PLUMBING")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unClienteAutenticadoNoPuedeGestionarEspecialidades() throws Exception {
        register("/api/users/clients", "cliente@example.com", "1020304050");
        String token = login("cliente@example.com");

        getWithToken(CATEGORIES, token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
        updateCategories(token, List.of("PLUMBING")).andExpect(status().isForbidden());
        assertThat(storedCategoryRows()).isZero();
    }

    @Test
    void unaCuentaProfessionalSinPerfilDeTecnicoResponde404() throws Exception {
        userRepository.save(new User("sinperfil@example.com", passwordEncoder.encode(PASSWORD), Role.PROFESSIONAL,
                "Sin", "Perfil", DocumentType.CC, "33333333", "3001112233", "v1.0", Instant.now()));
        String token = login("sinperfil@example.com");

        getWithToken(CATEGORIES, token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Perfil no encontrado"));
        updateCategories(token, List.of("PLUMBING")).andExpect(status().isNotFound());
    }

    // ---------- Concurrencia (DEF-QA-02) ----------

    @Test
    void variasActualizacionesSimultaneasDeEspecialidadesSeAplicanSinConflicto() throws Exception {
        UUID userId = registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");
        List<Callable<ResultActions>> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            List<String> categories = i % 2 == 0 ? List.of("PLUMBING", "ELECTRICAL") : List.of("PLUMBING", "PAINTING");
            requests.add(() -> updateCategories(token, categories));
        }

        assertThat(runAtOnce(requests)).containsOnly(200);

        assertThat(technicianRepository.findByUserId(userId).orElseThrow().getCategories())
                .hasSize(2).contains(ServiceCategory.PLUMBING);
        assertThat(storedCategoryRows()).isEqualTo(2);
    }

    @Test
    void variasActualizacionesSimultaneasDelPerfilSeAplicanSinConflicto() throws Exception {
        UUID userId = registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");
        List<Callable<ResultActions>> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Map<String, Object> body = profile(i % 2 == 0
                    ? List.of("PLUMBING", "MAINTENANCE") : List.of("PLUMBING", "CARPENTRY"));
            requests.add(() -> putJson(PROFILE, token, body));
        }

        assertThat(runAtOnce(requests)).containsOnly(200);

        assertThat(technicianRepository.findByUserId(userId).orElseThrow().getCategories())
                .hasSize(2).contains(ServiceCategory.PLUMBING);
        assertThat(storedCategoryRows()).isEqualTo(2);
    }
}
