package com.fixia.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos del perfil que el cliente puede editar (GC-317): nombre, apellido y teléfono, con las mismas reglas que el
 * registro. El correo, el documento, el rol y el estado no viajan aquí: cualquier otro campo que llegue en el cuerpo
 * se ignora. Reemplaza los tres valores por completo.
 */
public record ClientProfileRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String firstName,
        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100, message = "El apellido no puede superar 100 caracteres")
        String lastName,
        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = "^(\\+?[0-9]{7,15})?$", message = "El teléfono no es válido")
        String phone
) {
    /** Evita que datos personales aparezcan en logs si el objeto se imprime. */
    @Override
    public String toString() {
        return "ClientProfileRequest[]";
    }
}
