package com.fixia.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Size(max = 254, message = "El correo electrónico no puede superar 254 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 128, message = "La contraseña no puede superar 128 caracteres")
        String password
) {

    /** Evita que la contraseña aparezca en logs si el objeto se imprime. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
