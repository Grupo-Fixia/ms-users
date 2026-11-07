package com.fixia.users.admin;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores de las consultas del ADMIN con la misma forma {@code ProblemDetail} que el resto del servicio. Solo aplica a
 * {@link AdminController}.
 */
@RestControllerAdvice(assignableTypes = AdminController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminExceptionHandler {

    @ExceptionHandler(InvalidAdminQueryException.class)
    public ProblemDetail handleInvalidQuery(InvalidAdminQueryException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Consulta inválida", ex.getMessage());
    }

    /** Un id que no es UUID en la ruta o en un filtro. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Consulta inválida", "El valor de " + ex.getName() + " no es válido");
    }

    @ExceptionHandler(AdminUserNotFoundException.class)
    public ProblemDetail handleNotFound(AdminUserNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Usuario no encontrado", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
