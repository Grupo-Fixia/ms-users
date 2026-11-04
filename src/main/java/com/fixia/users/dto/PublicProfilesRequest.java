package com.fixia.users.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Consulta interna de ms-services (GC-425): los técnicos ({@code users.id}) de los que quiere el perfil público, por
 * ejemplo los postulantes de una solicitud. Acepta varios en una sola llamada, hasta {@value #MAX_USERS}, igual que la
 * consulta de elegibilidad.
 */
public record PublicProfilesRequest(
        @NotEmpty(message = "Debe indicar al menos un técnico")
        @Size(max = MAX_USERS, message = "Se permiten máximo " + MAX_USERS + " técnicos por consulta")
        List<@NotNull(message = "El técnico no es válido") UUID> userIds
) {

    public static final int MAX_USERS = 100;
}
