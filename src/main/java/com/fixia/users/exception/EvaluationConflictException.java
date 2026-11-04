package com.fixia.users.exception;

/** Ya existe otra evaluación en esa dirección para ese servicio (GC-444, RN-012). Se traduce a 409. */
public class EvaluationConflictException extends RuntimeException {

    public static final String EVALUATION_ALREADY_EXISTS = "EVALUATION_ALREADY_EXISTS";

    public EvaluationConflictException() {
        super("Ya registró una evaluación distinta para este servicio");
    }
}
