package com.fixia.users;

import com.fixia.users.config.QaSeedLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationContextTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void elContextoArrancaYLasMigracionesCoincidenConElModelo() {
        // ddl-auto=validate falla el arranque si las tablas de Flyway no coinciden con las entidades.
    }

    @Test
    void sinElPerfilSeedQaNoExisteElCargadorDeCuentasDeQa() {
        // Las cuentas de QA tienen una contraseña conocida: sin el perfil no debe haber forma de cargarlas.
        assertThat(context.getBeansOfType(QaSeedLoader.class)).isEmpty();
    }

    @Test
    void elHealthCheckEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
