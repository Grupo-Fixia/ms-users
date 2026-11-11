package com.fixia.users.exception;

import java.util.List;

/** La invitación no admite la operación en su estado actual (GC-523). Se traduce a 409. */
public class InvitationConflictException extends RuntimeException {

    /** Ya hay una invitación pendiente de esa empresa a ese técnico. */
    public static final String INVITATION_ALREADY_PENDING = "INVITATION_ALREADY_PENDING";

    /** La invitación ya no está pendiente (respondida, cancelada o vencida). */
    public static final String INVITATION_NOT_PENDING = "INVITATION_NOT_PENDING";

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

    public List<String> getReasons() {
        return List.of(reason);
    }
}
