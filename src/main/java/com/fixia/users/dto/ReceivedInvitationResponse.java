package com.fixia.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fixia.users.domain.InvitationDisplayStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una invitación que recibió el técnico autenticado (GC-524, contrato de empresas §5). De la empresa solo se muestra el
 * nombre. {@code respondedAt} aparece cuando ya la respondió.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReceivedInvitationResponse(
        UUID id,
        InvitationDisplayStatus status,
        CompanyName company,
        Instant createdAt,
        Instant expiresAt,
        Instant respondedAt
) {

    public record CompanyName(String name) {
    }

    /** Las invitaciones pendientes y vigentes del técnico, la más reciente primero. */
    public record Page(List<ReceivedInvitationResponse> invitations) {
    }
}
