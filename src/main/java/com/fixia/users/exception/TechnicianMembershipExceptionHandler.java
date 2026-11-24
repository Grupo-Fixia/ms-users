package com.fixia.users.exception;

import com.fixia.users.controller.TechnicianMembershipController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores del técnico ante invitaciones y desvinculación (GC-524), con la misma forma {@code ProblemDetail} que el resto
 * del servicio. Solo aplica a {@link TechnicianMembershipController}; las sesiones inválidas siguen en el manejador
 * común.
 */
@RestControllerAdvice(assignableTypes = TechnicianMembershipController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TechnicianMembershipExceptionHandler {

    @ExceptionHandler(InvitationConflictException.class)
    public ProblemDetail handleConflict(InvitationConflictException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Invitación en conflicto", ex.getMessage());
        problem.setProperty("reasons", ex.getReasons());
        return problem;
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    public ProblemDetail handleInvitationNotFound(InvitationNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Invitación no encontrada", ex.getMessage());
    }

    @ExceptionHandler(NotCompanyMemberException.class)
    public ProblemDetail handleNotMember(NotCompanyMemberException ex) {
        return problem(HttpStatus.NOT_FOUND, "Sin empresa", ex.getMessage());
    }

    @ExceptionHandler(LeaveBlockedException.class)
    public ProblemDetail handleLeaveBlocked(LeaveBlockedException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "No puedes desvincularte", ex.getMessage());
        problem.setProperty("reasons", ex.getReasons());
        problem.setProperty("serviceRequestIds", ex.getServiceRequestIds());
        return problem;
    }

    /** ms-services o ms-payments no respondieron: no se desvincula (D6, falla cerrado). */
    @ExceptionHandler(ServicesUnavailableException.class)
    public ProblemDetail handleUnavailable(ServicesUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                "No se pudo verificar tus trabajos y saldos en este momento. Intenta de nuevo más tarde");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
