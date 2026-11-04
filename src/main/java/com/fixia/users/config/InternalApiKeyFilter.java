package com.fixia.users.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Autentica las llamadas entre microservicios por la cabecera {@value #HEADER}.
 *
 * <p>Si la clave es válida deja una autenticación con la autoridad {@value #AUTHORITY}; si no lo es, no hace
 * nada y la cadena de seguridad responde 401 por su punto de entrada habitual. No es un {@code @Component}
 * a propósito: Spring Boot lo registraría como filtro de servlet para todas las rutas.
 *
 * <p>La comparación se hace sobre los hashes SHA-256 con {@link MessageDigest#isEqual}, que no depende del
 * contenido ni de la longitud de la clave enviada.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";
    public static final String AUTHORITY = "ROLE_INTERNAL";

    private final byte[] expectedDigest;

    public InternalApiKeyFilter(InternalApiProperties properties) {
        this.expectedDigest = properties.hasApiKey() ? sha256(properties.apiKey()) : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (isValid(provided)) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    "internal-service", null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }

    private boolean isValid(String provided) {
        return expectedDigest != null
                && provided != null
                && !provided.isEmpty()
                && MessageDigest.isEqual(expectedDigest, sha256(provided));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
