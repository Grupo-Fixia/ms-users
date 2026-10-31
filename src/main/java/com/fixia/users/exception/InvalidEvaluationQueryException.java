package com.fixia.users.exception;

/** La consulta de evaluaciones no trae entre 1 y 50 servicios (GC-444). Se traduce a 400. */
public class InvalidEvaluationQueryException extends RuntimeException {

    public InvalidEvaluationQueryException(String message) {
        super(message);
    }
}
