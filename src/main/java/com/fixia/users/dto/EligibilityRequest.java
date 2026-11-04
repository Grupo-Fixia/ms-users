package com.fixia.users.dto;

import com.fixia.users.domain.ServiceCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consulta interna de ms-services: las solicitudes para las que quiere saber si el técnico es elegible. Acepta varias
 * en una sola llamada (hasta {@value #MAX_REQUESTS}) para que las reglas de compatibilidad (GC-337) no hagan una
 * llamada por solicitud.
 */
public record EligibilityRequest(
        @NotEmpty(message = "Debe indicar al menos una solicitud")
        @Size(max = MAX_REQUESTS, message = "Se permiten máximo " + MAX_REQUESTS + " solicitudes por consulta")
        List<@NotNull(message = "La solicitud no es válida") @Valid RequestData> requests
) {

    public static final int MAX_REQUESTS = 100;

    /** Lo que ms-services sabe de cada solicitud: categoría, ubicación aproximada y momento esperado. */
    public record RequestData(
            @NotNull(message = "El id de la solicitud es obligatorio")
            UUID requestId,

            @NotNull(message = "La categoría es obligatoria")
            ServiceCategory category,

            @NotBlank(message = "La ciudad es obligatoria")
            String city,

            Double latitude,

            Double longitude,

            @NotNull(message = "El momento esperado es obligatorio")
            Instant expectedAt
    ) {
    }
}
