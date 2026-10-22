package com.fixia.users.exception;

/**
 * Dato del perfil del técnico que no cumple una regla del dominio (zona de atención, franjas de disponibilidad).
 * Extiende {@link IllegalArgumentException} porque es eso, un argumento inválido; tener su propio tipo permite
 * responder 400 con el mensaje del dominio en vez de un 500. Los mensajes no incluyen datos personales.
 */
public class InvalidTechnicianDataException extends IllegalArgumentException {

    public InvalidTechnicianDataException(String message) {
        super(message);
    }
}
