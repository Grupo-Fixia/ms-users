package com.fixia.users.dto;

import com.fixia.users.domain.Company;

import java.util.UUID;

/** Datos de la empresa que administra la cuenta autenticada (GC-466). */
public record CompanyResponse(UUID id, String name, String nit, Reputation reputation) {

    /** {@code reputation} (GC-551): promedio simple de sus técnicos actuales con evaluaciones; {@code null} si ninguno. */
    public static CompanyResponse from(Company company, Reputation reputation) {
        return new CompanyResponse(company.getId(), company.getName(), company.getNit(), reputation);
    }
}
