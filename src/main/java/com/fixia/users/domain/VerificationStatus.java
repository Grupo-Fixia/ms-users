package com.fixia.users.domain;

/** Estados de verificación del técnico (DD V2: PENDIENTE, VIGENTE, RECHAZADA, VENCIDA). */
public enum VerificationStatus {
    PENDING,
    VALID,
    REJECTED,
    EXPIRED
}
