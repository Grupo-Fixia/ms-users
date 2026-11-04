package com.fixia.users.exception;

public class CompanyNotFoundException extends RuntimeException {

    public CompanyNotFoundException() {
        super("La cuenta no administra ninguna empresa");
    }
}
