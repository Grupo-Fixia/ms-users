package com.fixia.users.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiKeyFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Ejecuta el filtro y devuelve la autenticación que dejó vista por el siguiente eslabón de la cadena. */
    private static Authentication run(InternalApiKeyFilter filter, String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/tokens/x/status");
        if (header != null) {
            request.addHeader(InternalApiKeyFilter.HEADER, header);
        }
        Authentication[] seen = new Authentication[1];
        FilterChain chain = (req, res) -> seen[0] = SecurityContextHolder.getContext().getAuthentication();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return seen[0];
    }

    @Test
    void conLaClaveCorrectaAutenticaConLaAutoridadInterna() throws Exception {
        InternalApiKeyFilter filter = new InternalApiKeyFilter(new InternalApiProperties("secreto"));

        Authentication authentication = run(filter, "secreto");

        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString).containsExactly(InternalApiKeyFilter.AUTHORITY);
    }

    @Test
    void conUnaClaveDistintaOSinCabeceraNoAutentica() throws Exception {
        InternalApiKeyFilter filter = new InternalApiKeyFilter(new InternalApiProperties("secreto"));

        assertThat(run(filter, "otro")).isNull();
        assertThat(run(filter, null)).isNull();
        assertThat(run(filter, "")).isNull();
        assertThat(run(filter, "secreto ")).isNull();
    }

    @Test
    void sinClaveConfiguradaNuncaAutenticaAunqueLaCabeceraVayaVacia() throws Exception {
        for (String configured : new String[]{null, "", "   "}) {
            InternalApiKeyFilter filter = new InternalApiKeyFilter(new InternalApiProperties(configured));

            assertThat(run(filter, "")).isNull();
            assertThat(run(filter, "   ")).isNull();
            assertThat(run(filter, "cualquiera")).isNull();
            assertThat(run(filter, null)).isNull();
        }
    }

    @Test
    void laClaveNoApareceEnElToString() {
        assertThat(new InternalApiProperties("secreto").toString()).doesNotContain("secreto").contains("configurada");
        assertThat(new InternalApiProperties("").toString()).contains("no configurada");
    }
}
