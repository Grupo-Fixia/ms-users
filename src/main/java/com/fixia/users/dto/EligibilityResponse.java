package com.fixia.users.dto;

import com.fixia.users.domain.IneligibilityReason;

import java.util.List;
import java.util.UUID;

/** Un resultado por solicitud consultada, en el mismo orden. {@code reasons} queda vacío si es elegible. */
public record EligibilityResponse(List<Result> results) {

    public record Result(UUID requestId, boolean eligible, List<IneligibilityReason> reasons) {
    }
}
