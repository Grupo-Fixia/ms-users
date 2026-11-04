package com.fixia.users.dto;

import com.fixia.users.domain.CoverageZone;
import com.fixia.users.domain.ServiceCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Consulta interna de ms-matching-geo: los técnicos que ofrecen una categoría y atienden una ciudad. El filtro por
 * círculo lo aplica quien consulta con los datos que recibe en la respuesta. {@code cursor} es el
 * {@code nextCursor} de la página anterior; sin él se empieza desde el principio.
 *
 * <p>{@code expectedAt} es el momento esperado de la solicitud (GC-474): si viene, solo se devuelven los técnicos con una
 * franja de disponibilidad que lo incluya (hora de Colombia, igual que la elegibilidad); sin él no se filtra por horario.
 */
public record TechnicianSearchRequest(
        @NotNull(message = "La categoría es obligatoria")
        ServiceCategory category,

        @NotBlank(message = "La ciudad es obligatoria")
        @Size(max = CoverageZone.CITY_MAX_LENGTH,
                message = "La ciudad no puede superar " + CoverageZone.CITY_MAX_LENGTH + " caracteres")
        String city,

        @Min(value = 1, message = "El límite debe ser al menos 1")
        @Max(value = MAX_LIMIT, message = "El límite no puede superar " + MAX_LIMIT)
        Integer limit,

        String cursor,

        Instant expectedAt
) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    /** El límite pedido o, si no lo indicó, {@value #DEFAULT_LIMIT}. */
    public int effectiveLimit() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
