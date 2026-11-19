package com.fixia.users.exception;

import com.fixia.users.controller.CompanyInvitationController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores de las invitaciones empresariales (GC-523), con la misma forma {@code ProblemDetail} que el resto del
 * servicio. Solo aplica a {@link CompanyInvitationController}; la validación del cuerpo y las sesiones inválidas siguen
 * en el manejador común. Va aparte para que un choque de la restricción única no caiga en el 409 genérico de «cuenta
 * ya registrada».
 */
@RestControllerAdvice(assignableTypes = CompanyInvitationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CompanyInvitationExceptionHandler {

    @ExceptionHandler(NotInvitableException.class)
    public ProblemDetail handleNotInvitable(NotInvitableException ex) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invitación no permitida", ex.getMessage());
        problem.setProperty("reasons", ex.getReasons());
        return problem;
    }

    @ExceptionHandler(InvitationConflictException.class)
    public ProblemDetail handleConflict(InvitationConflictException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Invitación en conflicto", ex.getMessage());
        problem.setProperty("reasons", ex.getReasons());
        return problem;
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    public ProblemDetail handleNotFound(InvitationNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Invitación no encontrada", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
