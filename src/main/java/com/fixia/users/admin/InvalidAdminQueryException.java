package com.fixia.users.admin;

/** Un filtro, un límite o un cursor de una consulta administrativa no es válido (400). */
public class InvalidAdminQueryException extends RuntimeException {

    public InvalidAdminQueryException(String message) {
        super(message);
    }
}
