package com.fixia.users.dto;

import com.fixia.users.domain.ServiceCategory;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Especialidades y servicios ofrecidos por el técnico (RF-010). Reemplaza por completo los anteriores; la lista
 * puede ir vacía porque la especialidad no es obligatoria (RN-019).
 */
public record TechnicianCategoriesRequest(
        @NotNull(message = "Debe indicar las categorías")
        Set<@NotNull(message = "La categoría no es válida") ServiceCategory> categories
) {
}
