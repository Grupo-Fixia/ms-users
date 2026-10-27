package com.fixia.users.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Consulta interna de ms-services (GC-468): ¿el técnico pertenece a la empresa que administra esta cuenta? Se pregunta
 * por la cuenta del administrador ({@code users.id}, el {@code sub} del token que ms-services ya conoce) y no por el id
 * de la empresa, que ms-services no tiene.
 */
public record TechnicianAssociationRequest(
        @NotNull(message = "Debe indicar la cuenta del administrador de la empresa")
        UUID companyAdminUserId,
        @NotNull(message = "Debe indicar el técnico")
        UUID technicianUserId
) {
}
