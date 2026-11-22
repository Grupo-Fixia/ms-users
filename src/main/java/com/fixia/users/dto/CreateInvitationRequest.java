package com.fixia.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Invitación de una empresa a un técnico por su correo exacto (GC-523). */
public record CreateInvitationRequest(
        @NotBlank(message = "El correo del técnico es obligatorio")
        @Email(message = "El correo del técnico no es válido")
        @Size(max = 254, message = "El correo del técnico es demasiado largo")
        String technicianEmail
) {
}
