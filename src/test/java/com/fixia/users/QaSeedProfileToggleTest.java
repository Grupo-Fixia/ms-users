package com.fixia.users;

import com.fixia.users.config.QaSeedLoader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Activar y quitar el perfil {@code seed-qa} sobre la misma base no debe impedir el arranque (GC-466). Cuando el seed era
 * una migración repetible de Flyway, un entorno que lo aplicaba y luego arrancaba sin el perfil dejaba de iniciar
 * ("Detected applied migration not resolved locally"). Se levantan tres contextos seguidos sobre una H2 con nombre, como
 * si fueran tres arranques del mismo servicio.
 */
class QaSeedProfileToggleTest {

    private static final String URL = "jdbc:h2:mem:users-seed-toggle;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
            + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";

    /** La URL va como argumento: tiene mayor precedencia que la del application-test.yml del perfil «test». */
    private ConfigurableApplicationContext start(String... profiles) {
        return new SpringApplicationBuilder(Application.class)
                .profiles(profiles)
                .run("--spring.datasource.url=" + URL, "--server.port=0");
    }

    private static int count(ConfigurableApplicationContext context, String table) {
        Integer total = context.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return total == null ? 0 : total;
    }

    @Test
    void quitarElPerfilNoImpideArrancarNiBorraLasCuentasYVolverAPonerloNoDuplica() {
        try (ConfigurableApplicationContext seeded = start("test", "seed-qa")) {
            assertThat(seeded.getBeansOfType(QaSeedLoader.class)).hasSize(1);
            assertThat(count(seeded, "users")).isEqualTo(7);
            assertThat(count(seeded, "company_technicians")).isEqualTo(4);
        }

        try (ConfigurableApplicationContext withoutProfile = start("test")) {
            assertThat(withoutProfile.getBeansOfType(QaSeedLoader.class)).isEmpty();
            // Las cuentas ya cargadas se conservan: el servicio arrancó con las mismas migraciones validadas.
            assertThat(count(withoutProfile, "users")).isEqualTo(7);
            assertThat(withoutProfile.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL", Integer.class))
                    .isEqualTo(8);
        }

        try (ConfigurableApplicationContext again = start("test", "seed-qa")) {
            assertThat(count(again, "users")).isEqualTo(7);
            assertThat(count(again, "companies")).isEqualTo(3);
            assertThat(count(again, "company_technicians")).isEqualTo(4);
        }
    }
}
