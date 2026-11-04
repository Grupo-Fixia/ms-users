package com.fixia.users.exception;

/**
 * Dato del perfil del cliente que no cumple una regla del dominio. Extiende {@link IllegalArgumentException} porque es
 * eso, un argumento inválido; tener su propio tipo permite responder 400 con el mensaje del dominio en vez de un 500.
 * Los mensajes no incluyen datos personales.
 */
public class InvalidClientDataException extends IllegalArgumentException {

    public InvalidClientDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
