package com.fixia.users.domain;

/** Estado guardado de una invitación empresarial (GC-523). La caducidad no se guarda: ver {@link InvitationDisplayStatus}. */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED
}
