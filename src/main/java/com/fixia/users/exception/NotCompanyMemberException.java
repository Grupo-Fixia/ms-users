package com.fixia.users.exception;

/** El técnico autenticado no pertenece a ninguna empresa (GC-524). Se traduce a 404. */
public class NotCompanyMemberException extends RuntimeException {

    public NotCompanyMemberException() {
        super("No perteneces a ninguna empresa");
    }
}
