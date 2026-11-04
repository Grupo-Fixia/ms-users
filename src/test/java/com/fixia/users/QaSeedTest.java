package com.fixia.users;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

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
        assertThat(users).isEqualTo(7);
        assertThat(technicians).isEqualTo(4);
        assertThat(companies).isEqualTo(3);
        assertThat(associations).isEqualTo(4);

        new ResourceDatabasePopulator(new ClassPathResource("seed/qa_empresas.sql")).execute(dataSource);

        assertThat(count("users")).isEqualTo(users);
        assertThat(count("technicians")).isEqualTo(technicians);
        assertThat(count("technician_categories")).isEqualTo(categories);
        assertThat(count("companies")).isEqualTo(companies);
        assertThat(count("company_technicians")).isEqualTo(associations);
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
