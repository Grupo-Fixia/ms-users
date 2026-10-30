package com.fixia.users.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Consulta interna de ms-services (GC-468): los técnicos de la empresa que administra esta cuenta ({@code users.id},
 * el {@code sub} del token con el que la empresa consulta oportunidades).
 */
public record CompanyTechniciansQuery(
        @NotNull(message = "Debe indicar la cuenta del administrador de la empresa")
        UUID companyAdminUserId
) {
}
