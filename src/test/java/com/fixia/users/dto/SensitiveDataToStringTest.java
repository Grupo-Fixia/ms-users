package com.fixia.users.dto;

import com.fixia.users.domain.DocumentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Los DTO con secretos no deben exponerlos si alguien los imprime por error en un log. */
class SensitiveDataToStringTest {

    @Test
    void loginNoImprimeLaContrasena() {
        assertThat(new LoginRequest("ana@example.com", "Clave1234").toString())
                .contains("ana@example.com").doesNotContain("Clave1234");
    }

    @Test
    void refreshNoImprimeElToken() {
        assertThat(new RefreshRequest("token-secreto").toString()).doesNotContain("token-secreto");
    }

    @Test
    void logoutNoImprimeElToken() {
        assertThat(new LogoutRequest("token-secreto").toString()).doesNotContain("token-secreto");
    }

    @Test
    void laRespuestaDeTokensNoImprimeLosTokens() {
        assertThat(new TokenResponse("access-secreto", "refresh-secreto", "Bearer", 900).toString())
                .doesNotContain("access-secreto").doesNotContain("refresh-secreto");
    }

    @Test
    void elRegistroNoImprimeLaContrasena() {
        ClientRegistrationRequest request = new ClientRegistrationRequest("Ana", "Pérez", DocumentType.CC,
                "123", "ana@example.com", "3001112233", "Clave1234", "v1.0", true);

        assertThat(request.toString()).doesNotContain("Clave1234");
    }

    @Test
    void elRegistroDeTecnicoNoImprimeLaContrasena() {
        TechnicianRegistrationRequest request = new TechnicianRegistrationRequest("Carlos", "Gómez", DocumentType.CC,
                "456", "carlos@example.com", "3104445566", "Tecnico123", "v1.0", true);

        assertThat(request.toString()).contains("carlos@example.com").doesNotContain("Tecnico123");
    }
}
