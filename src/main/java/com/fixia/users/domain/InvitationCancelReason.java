package com.fixia.users.domain;

/** Por qué una invitación quedó {@link InvitationStatus#CANCELLED} (GC-523). */
public enum InvitationCancelReason {
    /** La empresa la canceló mientras estaba pendiente. */
    BY_COMPANY,
    /** El técnico aceptó la invitación de otra empresa (GC-524). */
    JOINED_OTHER_COMPANY,
    /** Estaba vencida cuando la empresa volvió a invitar al mismo técnico: se cierra para liberar la pendiente. */
    EXPIRED
}
