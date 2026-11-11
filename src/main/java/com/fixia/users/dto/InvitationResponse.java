package com.fixia.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fixia.users.domain.InvitationDisplayStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Una invitación de la empresa autenticada (GC-523). El técnico se muestra con nombre corto («Carlos G.»), nunca con
 * correo ni documento. {@code respondedAt} solo aparece cuando la invitación ya se cerró.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvitationResponse(
        UUID id,
        InvitationDisplayStatus status,
        Technician technician,
        Instant createdAt,
        Instant expiresAt,
        Instant respondedAt
) {

    public record Technician(String displayName) {
    }
}
