package com.fixia.users.dto;

import com.fixia.users.domain.Company;

import java.util.UUID;

/** Datos de la empresa que administra la cuenta autenticada (GC-466). */
public record CompanyResponse(UUID id, String name, String nit) {

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(company.getId(), company.getName(), company.getNit());
    }
}
