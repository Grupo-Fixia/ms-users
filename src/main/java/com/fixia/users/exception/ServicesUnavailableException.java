package com.fixia.users.exception;

/** ms-services no respondió o respondió algo inválido al verificar una evaluación (GC-444). Se traduce a 503. */
public class ServicesUnavailableException extends RuntimeException {

    public ServicesUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
