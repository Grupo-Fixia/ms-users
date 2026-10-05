package com.fixia.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @NotBlank(message = "El refresh token es obligatorio")
        @Size(max = 200, message = "El refresh token no es válido")
        String refreshToken
) {

    /** Evita que el token aparezca en logs si el objeto se imprime. */
    @Override
    public String toString() {
        return "LogoutRequest[]";
    }
}
