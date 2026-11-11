package com.fixia.users.dto;

import java.util.List;

/** Invitaciones de la empresa autenticada, de la más reciente a la más antigua (GC-523). */
public record InvitationsResponse(List<InvitationResponse> invitations) {
}
