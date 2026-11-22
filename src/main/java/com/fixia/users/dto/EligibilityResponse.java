package com.fixia.users.dto;

import com.fixia.users.domain.IneligibilityReason;

import java.util.List;
import java.util.UUID;

/** Un resultado por solicitud consultada, en el mismo orden. {@code reasons} queda vacío si es elegible. */
public record EligibilityResponse(List<Result> results) {

    /**
     * @param companyMember el técnico pertenece a una empresa (contrato de empresas, D9). No cambia {@code eligible}:
     *                      la misma consulta sirve cuando su empresa lo propone. ms-services lo usa para impedir que se
     *                      postule o vea oportunidades por su cuenta.
     */
    public record Result(UUID requestId, boolean eligible, List<IneligibilityReason> reasons, boolean companyMember) {
    }
}
