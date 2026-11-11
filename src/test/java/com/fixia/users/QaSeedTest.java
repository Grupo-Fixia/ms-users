package com.fixia.users;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.IneligibilityReason;
import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.dto.EligibilityRequest;
import com.fixia.users.dto.EligibilityResponse;
import com.fixia.users.service.TechnicianEligibilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Datos de QA de GC-466 (perfil {@code seed-qa}). Corre sobre su propia base en memoria: el seed no debe quedar en la
 * base compartida por el resto de las pruebas, que asumen que empieza vacía.
 */
@SpringBootTest(properties = "spring.datasource.url="
        + "jdbc:h2:mem:users-seed-qa;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "seed-qa"})
class QaSeedTest {

    private static final String QA_PASSWORD = "FixiaQA#2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TechnicianEligibilityService eligibilityService;

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", QA_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }

    private int count(String table) {
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return total == null ? 0 : total;
    }

    @Test
    void cadaEmpresaDeQaVeSoloSusTecnicos() throws Exception {
        mockMvc.perform(get("/api/users/companies/me/technicians")
                        .header(HttpHeaders.AUTHORIZATION, login("empresa1@fixia-qa.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians[*].displayName", contains("Carlos G.", "Diana T.")))
                .andExpect(jsonPath("$.technicians[0].verificationStatus").value("VALID"))
                .andExpect(jsonPath("$.technicians[0].categories", contains("PLUMBING", "MAINTENANCE")));

        mockMvc.perform(get("/api/users/companies/me/technicians")
                        .header(HttpHeaders.AUTHORIZATION, login("empresa2@fixia-qa.test")))
                .andExpect(jsonPath("$.technicians[*].displayName", contains("Julián P.", "Sofía M.")));
    }

    @Test
    void laEmpresaDeQaSinTecnicosRecibeListaVacia() throws Exception {
        mockMvc.perform(get("/api/users/companies/me")
                        .header(HttpHeaders.AUTHORIZATION, login("empresa3@fixia-qa.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hogar Experto (sin técnicos)"));
        mockMvc.perform(get("/api/users/companies/me/technicians")
                        .header(HttpHeaders.AUTHORIZATION, login("empresa3@fixia-qa.test")))
                .andExpect(jsonPath("$.technicians", hasSize(0)));
    }

    @Test
    void losTecnicosDeQaTambienPuedenIniciarSesionYNoUsanLasRutasDeEmpresa() throws Exception {
        mockMvc.perform(get("/api/users/companies/me/technicians")
                        .header(HttpHeaders.AUTHORIZATION, login("tecnico1@fixia-qa.test")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elSeedNoDejaHuellaEnElHistorialDeFlyway() {
        // Regresión: como migración repetible quedaba en flyway_schema_history y, al quitar el perfil, el servicio no
        // arrancaba ("applied migration not resolved locally"). Solo deben figurar las migraciones versionadas.
        // Una repetible es una migración SQL sin versión (la fila de creación del esquema es de otro tipo).
        Integer repeatables = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE description LIKE '%seed%' OR (version IS NULL AND type = 'SQL')", Integer.class);
        assertThat(repeatables).isZero();
        Integer versioned = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL", Integer.class);
        assertThat(versioned).isEqualTo(versionedMigrations()).isGreaterThanOrEqualTo(9);
    }

    @Test
    void ejecutarElSeedOtraVezNoDuplicaNada() throws Exception {
        int users = count("users");
        int technicians = count("technicians");
        int categories = count("technician_categories");
        int companies = count("companies");
        int associations = count("company_technicians");
        int slots = count("technician_availability");
        assertThat(users).isEqualTo(8); // 3 empresas, 4 técnicos y el ADMIN (GC-503)
        assertThat(technicians).isEqualTo(4);
        assertThat(companies).isEqualTo(3);
        assertThat(associations).isEqualTo(4);
        assertThat(slots).isEqualTo(28); // 4 técnicos x 7 días (GC-504)

        new ResourceDatabasePopulator(new ClassPathResource("seed/qa_empresas.sql"),
                new ClassPathResource("seed/qa_admin.sql")).execute(dataSource);

        assertThat(count("users")).isEqualTo(users);
        assertThat(count("technicians")).isEqualTo(technicians);
        assertThat(count("technician_categories")).isEqualTo(categories);
        assertThat(count("companies")).isEqualTo(companies);
        assertThat(count("company_technicians")).isEqualTo(associations);
        assertThat(count("technician_availability")).isEqualTo(slots);
    }

    /** Regresión de GC-504: sin franjas, el filtro AVAILABILITY de GC-474 dejaba a todos los técnicos del seed sin trabajo. */
    @Test
    void losTecnicosDeQaSonElegiblesDentroDeSuHorario() {
        ZoneId bogota = ZoneId.of("America/Bogota");
        // Un miércoles a media mañana y un domingo a las 21:59: los 7 días tienen horario.
        Instant miercoles = ZonedDateTime.of(2026, 11, 11, 10, 0, 0, 0, bogota).toInstant();
        Instant domingo = ZonedDateTime.of(2026, 11, 15, 21, 59, 0, 0, bogota).toInstant();
        // Usuario, especialidad y ciudad de cada técnico, tal como los siembra qa_empresas.sql.
        Object[][] tecnicos = {
                {"7ec00000-0000-4000-8000-000000000001", ServiceCategory.PLUMBING, "Cali"},
                {"7ec00000-0000-4000-8000-000000000002", ServiceCategory.ELECTRICAL, "Cali"},
                {"7ec00000-0000-4000-8000-000000000003", ServiceCategory.PAINTING, "Palmira"},
                {"7ec00000-0000-4000-8000-000000000004", ServiceCategory.LOCKSMITHING, "Palmira"},
        };
        for (Object[] tecnico : tecnicos) {
            UUID userId = UUID.fromString((String) tecnico[0]);
            for (Instant momento : List.of(miercoles, domingo)) {
                assertThat(evaluar(userId, (ServiceCategory) tecnico[1], (String) tecnico[2], momento))
                        .as("%s en %s", tecnico[0], momento)
                        .isEmpty();
            }
        }
    }

    @Test
    void fueraDelHorarioDeQaSigueSiendoNoElegible() {
        ZoneId bogota = ZoneId.of("America/Bogota");
        UUID tecnico1 = UUID.fromString("7ec00000-0000-4000-8000-000000000001");
        Instant antes = ZonedDateTime.of(2026, 11, 11, 5, 59, 0, 0, bogota).toInstant();
        Instant fin = ZonedDateTime.of(2026, 11, 11, 22, 0, 0, 0, bogota).toInstant(); // el fin es exclusivo
        assertThat(evaluar(tecnico1, ServiceCategory.PLUMBING, "Cali", antes)).containsExactly(IneligibilityReason.AVAILABILITY);
        assertThat(evaluar(tecnico1, ServiceCategory.PLUMBING, "Cali", fin)).containsExactly(IneligibilityReason.AVAILABILITY);
    }

    /** El seed no pisa un horario configurado a mano (como el que ya tenía QA) ni deja franjas solapadas ese día. */
    @Test
    void elSeedRespetaElHorarioQueUnTecnicoYaTeniaEseDia() {
        UUID technicianId = jdbcTemplate.queryForObject(
                "SELECT id FROM technicians WHERE user_id = CAST('7ec00000-0000-4000-8000-000000000004' AS UUID)", UUID.class);
        jdbcTemplate.update("DELETE FROM technician_availability WHERE technician_id = ? AND day_of_week = 'MONDAY'", technicianId);
        jdbcTemplate.update("INSERT INTO technician_availability (id, technician_id, day_of_week, start_time, end_time) "
                + "VALUES (?, ?, 'MONDAY', CAST('08:00:00' AS TIME), CAST('12:00:00' AS TIME))", UUID.randomUUID(), technicianId);
        try {
            new ResourceDatabasePopulator(new ClassPathResource("seed/qa_empresas.sql")).execute(dataSource);

            Integer mondaySlots = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM technician_availability WHERE technician_id = ? AND day_of_week = 'MONDAY'",
                    Integer.class, technicianId);
            assertThat(mondaySlots).isEqualTo(1);
        } finally {
            // Deja la base como la dejó el seed para las demás pruebas.
            jdbcTemplate.update("DELETE FROM technician_availability WHERE technician_id = ? AND day_of_week = 'MONDAY'", technicianId);
            new ResourceDatabasePopulator(new ClassPathResource("seed/qa_empresas.sql")).execute(dataSource);
        }
        assertThat(count("technician_availability")).isEqualTo(28);
    }

    private List<IneligibilityReason> evaluar(UUID userId, ServiceCategory category, String city, Instant expectedAt) {
        EligibilityRequest.RequestData request =
                new EligibilityRequest.RequestData(UUID.randomUUID(), category, city, null, null, expectedAt);
        EligibilityResponse.Result result = eligibilityService.evaluate(userId, List.of(request)).results().get(0);
        return result.reasons();
    }

    @Test
    void elAdminDeQaIniciaSesionConRolAdmin() throws Exception {
        String token = login("admin@fixia-qa.test").substring("Bearer ".length());
        JsonNode claims = objectMapper.readTree(
                java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(claims.get("roles")).hasSize(1);
        assertThat(claims.get("roles").get(0).asText()).isEqualTo("ROLE_ADMIN");

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        // No es administrador de empresa: las rutas de empresa siguen cerradas para él.
        mockMvc.perform(get("/api/users/companies/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /** Migraciones versionadas del servicio: así la prueba no se rompe cada vez que se agrega una (GC-444). */
    static int versionedMigrations() {
        try {
            return new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql").length;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
