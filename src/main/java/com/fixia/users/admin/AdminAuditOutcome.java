package com.fixia.users.admin;

/** Resultado de una acción del ADMIN (contrato {@code docs/contracts/admin.md} §5). */
public enum AdminAuditOutcome {
    OK,
    DENIED,
    CONFLICT,
    NOT_FOUND
}
