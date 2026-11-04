package com.fixia.users.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credencial de las rutas internas ({@code /internal/**}), que consumen otros microservicios de Fixia.
 * Llega por la variable de entorno INTERNAL_API_KEY; nunca se guarda en el repositorio. Si no está
 * configurada, las rutas internas rechazan toda petición.
 */
@ConfigurationProperties(prefix = "fixia.internal")
public record InternalApiProperties(String apiKey) {

    boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Evita que la credencial aparezca en logs si el objeto se imprime. */
    @Override
    public String toString() {
        return "InternalApiProperties[apiKey=" + (hasApiKey() ? "configurada" : "no configurada") + "]";
    }
}
