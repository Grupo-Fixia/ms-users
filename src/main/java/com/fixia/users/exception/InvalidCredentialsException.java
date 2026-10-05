package com.fixia.users.exception;

/** Credenciales rechazadas. El mensaje es único para no revelar si falló el correo, la contraseña o el estado de la cuenta. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }
}
