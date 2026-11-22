package com.fixia.users.dto;

import java.time.Instant;

/** La empresa a la que pertenece el técnico autenticado y desde cuándo (GC-524). Solo el nombre de la empresa. */
public record MembershipResponse(ReceivedInvitationResponse.CompanyName company, Instant joinedAt) {
}
