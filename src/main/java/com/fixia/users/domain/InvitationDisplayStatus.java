package com.fixia.users.domain;

/**
 * Estado que ve quien consulta una invitación (GC-523): los guardados más {@link #EXPIRED}, que es una pendiente cuyo
 * plazo ya pasó, o una cancelada por vencimiento al volver a invitar.
 */
public enum InvitationDisplayStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
