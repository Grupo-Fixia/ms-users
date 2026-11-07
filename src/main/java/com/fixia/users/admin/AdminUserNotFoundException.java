package com.fixia.users.admin;

/** El usuario pedido por el ADMIN no existe (404). */
public class AdminUserNotFoundException extends RuntimeException {

    public AdminUserNotFoundException() {
        super("No existe un usuario con ese identificador");
    }
}
