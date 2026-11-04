package com.fixia.users.exception;

import com.fixia.users.controller.EvaluationController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Errores de las evaluaciones (GC-444), con la misma forma {@code ProblemDetail} que el resto del servicio. Solo
 * aplica a {@link EvaluationController}; la validación del cuerpo sigue en el manejador común.
 */
@RestControllerAdvice(assignableTypes = EvaluationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EvaluationExceptionHandler {

    @ExceptionHandler(EvaluableServiceNotFoundException.class)
    public ProblemDetail handleNotFound(EvaluableServiceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Servicio no encontrado", ex.getMessage());
    }

    @ExceptionHandler(EvaluationRejectedException.class)
    public ProblemDetail handleRejected(EvaluationRejectedException ex) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Evaluación no permitida", ex.getMessage());
        problem.setProperty("reasons", ex.getReasons());
        return problem;
    }

    @ExceptionHandler(EvaluationConflictException.class)
    public ProblemDetail handleConflict(EvaluationConflictException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Evaluación existente", ex.getMessage());
        problem.setProperty("reasons", List.of(EvaluationConflictException.EVALUATION_ALREADY_EXISTS));
        return problem;
    }

    @ExceptionHandler(ServicesUnavailableException.class)
    public ProblemDetail handleUnavailable(ServicesUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                "No se pudo verificar el servicio en este momento. Intente de nuevo más tarde");
    }

    @ExceptionHandler(InvalidEvaluationQueryException.class)
    public ProblemDetail handleInvalidQuery(InvalidEvaluationQueryException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Consulta inválida", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleForbidden(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Acceso denegado", "Su rol no permite realizar esta acción");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
