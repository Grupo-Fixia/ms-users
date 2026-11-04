package com.fixia.users.exception;

import java.util.List;

/**
 * La evaluación no cumple una regla de negocio (GC-444): servicio no cerrado, de empresa, fuera de plazo o con un
 * evaluado inexistente. Se traduce a 422 con los motivos en {@code reasons}.
 */
public class EvaluationRejectedException extends RuntimeException {

    public static final String SERVICE_NOT_CLOSED = "SERVICE_NOT_CLOSED";
    public static final String COMPANY_SERVICE_NOT_EVALUABLE = "COMPANY_SERVICE_NOT_EVALUABLE";
    public static final String EVALUATION_WINDOW_EXPIRED = "EVALUATION_WINDOW_EXPIRED";
    public static final String EVALUATED_USER_NOT_FOUND = "EVALUATED_USER_NOT_FOUND";

    private final List<String> reasons;

    public EvaluationRejectedException(String reason) {
        super("La evaluación no está permitida");
        this.reasons = List.of(reason);
    }

    public List<String> getReasons() {
        return reasons;
    }
}
