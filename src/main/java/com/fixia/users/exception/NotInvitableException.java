package com.fixia.users.exception;

import java.util.List;

/**
 * El correo no corresponde a un técnico independiente al que se pueda invitar (GC-523). Cubre tres casos con el mismo
 * mensaje a propósito: el correo no existe, la cuenta no es de un técnico activo o el técnico ya tiene empresa. Así la
 * respuesta no revela qué cuentas existen ni con quién trabajan (decisión D3 de docs/contracts/empresas.md). Se traduce
 * a 422.
 */
public class NotInvitableException extends RuntimeException {

    public static final String NOT_INVITABLE = "NOT_INVITABLE";

    public NotInvitableException() {
        super("No es posible invitar a esa cuenta");
    }

    public List<String> getReasons() {
        return List.of(NOT_INVITABLE);
    }
}
