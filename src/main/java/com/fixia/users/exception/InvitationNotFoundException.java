package com.fixia.users.exception;

/** La invitación no existe o es de otra empresa: las dos cosas se ven igual (GC-523). Se traduce a 404. */
public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException() {
        super("La invitación no existe");
    }
}
