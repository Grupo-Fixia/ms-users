package com.fixia.users.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Disponibilidad semanal del técnico (RF-011). Reemplaza por completo la anterior; la lista puede ir vacía. Las
 * franjas no pueden cruzarse entre sí.
 */
public record AvailabilityRequest(
        @NotNull(message = "Debe indicar las franjas de disponibilidad")
        List<@NotNull(message = "La franja no es válida") @Valid AvailabilitySlotRequest> slots
) {
}
