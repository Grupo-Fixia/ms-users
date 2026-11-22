package com.fixia.users.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Conexión con ms-payments para saber si un técnico deja dinero retenido al desvincularse de su empresa (GC-524). Los
 * valores por defecto sirven en QA, donde ambos servicios están en el namespace {@code apps}; se cambian con
 * {@code FIXIA_PAYMENTS_BASE_URL}, {@code FIXIA_PAYMENTS_CONNECT_TIMEOUT} y {@code FIXIA_PAYMENTS_READ_TIMEOUT}. La
 * clave es la misma {@code INTERNAL_API_KEY} de {@link InternalApiProperties}.
 */
@ConfigurationProperties(prefix = "fixia.payments")
public record PaymentsClientProperties(
        @DefaultValue("http://ms-payments:8080") String baseUrl,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout
) {
}
