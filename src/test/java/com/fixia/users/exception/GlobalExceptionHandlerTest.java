package com.fixia.users.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unaModificacionConcurrenteRespondeConflictoSinConfundirseConCuentaExistente() {
        ProblemDetail problem = handler.handleConcurrentUpdate(new OptimisticLockingFailureException("stale"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Modificación concurrente");
        assertThat(problem.getDetail()).doesNotContain("cuenta");
    }

    @Test
    void unDatoInvalidoDelTecnicoResponde400ConElMensajeDelDominio() {
        ProblemDetail problem = handler.handleInvalidTechnicianData(
                new InvalidTechnicianDataException("La ciudad es obligatoria"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Datos inválidos");
        assertThat(problem.getDetail()).isEqualTo("La ciudad es obligatoria");
    }
}
