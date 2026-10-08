package com.fixia.users.exception;

public class TechnicianProfileNotFoundException extends RuntimeException {

    public TechnicianProfileNotFoundException() {
        super("La cuenta no tiene un perfil de técnico");
    }
}
