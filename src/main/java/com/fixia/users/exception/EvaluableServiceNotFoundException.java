package com.fixia.users.exception;

/**
 * El servicio no existe o quien llama no participó en él (GC-444). Las dos situaciones responden igual (404) para que
 * un tercero no aprenda si el servicio existe.
 */
public class EvaluableServiceNotFoundException extends RuntimeException {

    public EvaluableServiceNotFoundException() {
        super("No existe un servicio en el que usted haya participado con ese identificador");
    }
}
