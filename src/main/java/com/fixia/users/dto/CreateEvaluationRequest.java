package com.fixia.users.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evaluación que envía un participante (GC-444). La dirección y el evaluado no vienen aquí: se deducen del token y de
 * la participación. La nota se recibe como decimal para rechazar {@code 4.5} (un entero la truncaría a 4 sin avisar).
 */
public record CreateEvaluationRequest(
        @NotNull(message = "Debe indicar el servicio")
        UUID serviceRequestId,

        @NotNull(message = "Debe indicar la calificación")
        @DecimalMin(value = "1", message = "La calificación va de 1 a 5")
        @DecimalMax(value = "5", message = "La calificación va de 1 a 5")
        @Digits(integer = 1, fraction = 0, message = "La calificación es un número entero de 1 a 5")
        BigDecimal rating,

        @Size(max = 500, message = "El comentario admite hasta 500 caracteres")
        String comment
) {
}
