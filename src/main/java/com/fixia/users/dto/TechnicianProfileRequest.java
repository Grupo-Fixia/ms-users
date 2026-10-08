package com.fixia.users.dto;

import com.fixia.users.domain.ServiceCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Información profesional básica del técnico (RF-009). Reemplaza por completo la anterior. */
public record TechnicianProfileRequest(
        @NotBlank(message = "La descripción profesional es obligatoria")
        @Size(max = 1000, message = "La descripción profesional no puede superar 1000 caracteres")
        String professionalDescription,

        @NotNull(message = "Los años de experiencia son obligatorios")
        @Min(value = 0, message = "Los años de experiencia no pueden ser negativos")
        @Max(value = 80, message = "Los años de experiencia no pueden superar 80")
        Short yearsOfExperience,

        @NotEmpty(message = "Debe declarar al menos una categoría")
        Set<@NotNull(message = "La categoría no es válida") ServiceCategory> categories
) {
}
