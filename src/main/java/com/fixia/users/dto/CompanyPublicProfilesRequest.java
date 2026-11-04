package com.fixia.users.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Consulta interna de ms-services (GC-468): las empresas postuladas a una solicitud, por la cuenta de su administrador
 * ({@code users.id}, el {@code sub} del token con el que se postularon). Hasta {@value #MAX_COMPANIES} por llamada,
 * como la consulta de perfiles de técnicos.
 */
public record CompanyPublicProfilesRequest(
        @NotEmpty(message = "Debe indicar al menos una empresa")
        @Size(max = MAX_COMPANIES, message = "Se permiten máximo " + MAX_COMPANIES + " empresas por consulta")
        List<@NotNull(message = "La empresa no es válida") UUID> companyAdminUserIds
) {

    public static final int MAX_COMPANIES = 100;
}
