package com.fixia.users.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Formato de error común del servicio: RFC 7807 (ProblemDetail).
 * Los mensajes nunca incluyen datos sensibles ni detalles internos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField).thenComparing(FieldError::getDefaultMessage))
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Hay datos que debe corregir");
        problem.setTitle("Datos inválidos");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido");
        problem.setTitle("Solicitud inválida");
        return problem;
    }

    @ExceptionHandler({AccountAlreadyExistsException.class, DataIntegrityViolationException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Ya existe una cuenta registrada con los datos proporcionados");
        problem.setTitle("Cuenta existente");
        return problem;
    }
}
