package com.fixia.users.exception;

/**
 * El cursor de la búsqueda de técnicos no es uno que este servicio haya emitido. Responde 400; el mensaje no repite el
 * valor recibido.
 */
public class InvalidSearchCursorException extends IllegalArgumentException {

    public InvalidSearchCursorException() {
        super("El cursor no es válido");
    }
}
