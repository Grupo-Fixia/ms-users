package com.fixia.users.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Conexión con ms-services para verificar la participación en un servicio antes de evaluarlo (GC-444). Los valores
 * por defecto sirven en QA, donde ambos servicios están en el mismo namespace; se cambian con las variables
 * {@code FIXIA_SERVICES_BASE_URL}, {@code FIXIA_SERVICES_CONNECT_TIMEOUT} y {@code FIXIA_SERVICES_READ_TIMEOUT}. La
 * clave interna es la misma {@code INTERNAL_API_KEY} de {@link InternalApiProperties}.
 */
@ConfigurationProperties(prefix = "fixia.services")
public record ServicesClientProperties(
        @DefaultValue("http://ms-services:8080") String baseUrl,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout
) {
}
