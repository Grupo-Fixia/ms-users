package com.fixia.users.exception;

public class AccountAlreadyExistsException extends RuntimeException {

    public AccountAlreadyExistsException() {
        super("Ya existe una cuenta registrada con los datos proporcionados");
    }
}
