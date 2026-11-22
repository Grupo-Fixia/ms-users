package com.fixia.users.exception;

import java.util.List;

/** La invitación no admite la operación en su estado actual (GC-523). Se traduce a 409. */
public class InvitationConflictException extends RuntimeException {

    /** Ya hay una invitación pendiente de esa empresa a ese técnico. */
    public static final String INVITATION_ALREADY_PENDING = "INVITATION_ALREADY_PENDING";

    /** La invitación ya no está pendiente (respondida, cancelada o vencida). */
    public static final String INVITATION_NOT_PENDING = "INVITATION_NOT_PENDING";

    /** El técnico ya pertenece a una empresa: no puede aceptar otra invitación (GC-524). */
    public static final String ALREADY_IN_COMPANY = "ALREADY_IN_COMPANY";

    /** La cuenta de la empresa que invitó ya no está activa (GC-524). */
    public static final String COMPANY_UNAVAILABLE = "COMPANY_UNAVAILABLE";

    private final String reason;

    private InvitationConflictException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public static InvitationConflictException alreadyPending() {
        return new InvitationConflictException(INVITATION_ALREADY_PENDING,
                "Ya hay una invitación pendiente para ese técnico");
    }

    public static InvitationConflictException notPending() {
        return new InvitationConflictException(INVITATION_NOT_PENDING, "La invitación ya no está pendiente");
    }

    public static InvitationConflictException alreadyInCompany() {
        return new InvitationConflictException(ALREADY_IN_COMPANY,
                "Ya perteneces a una empresa: desvincúlate antes de aceptar otra invitación");
    }

    public static InvitationConflictException companyUnavailable() {
        return new InvitationConflictException(COMPANY_UNAVAILABLE, "La empresa que te invitó ya no está disponible");
    }

    public List<String> getReasons() {
        return List.of(reason);
    }
}
