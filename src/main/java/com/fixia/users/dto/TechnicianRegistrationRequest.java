package com.fixia.users.dto;

import com.fixia.users.domain.DocumentType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mismos datos y reglas que {@link ClientRegistrationRequest}: la cuenta solo se diferencia por el rol. */
public record TechnicianRegistrationRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String firstName,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100, message = "El apellido no puede superar 100 caracteres")
        String lastName,

        @NotNull(message = "El tipo de documento es obligatorio")
        DocumentType documentType,

        @NotBlank(message = "El número de documento es obligatorio")
        @Size(max = 30, message = "El número de documento no puede superar 30 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9]*$", message = "El número de documento solo admite letras y números")
        String documentNumber,

        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El correo electrónico no es válido")
        @Size(max = 254, message = "El correo electrónico no puede superar 254 caracteres")
        String email,

        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = "^(\\+?[0-9]{7,15})?$", message = "El teléfono no es válido")
        String phone,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "La contraseña debe incluir letras y números")
        String password,

        @NotBlank(message = "La versión de la política de datos es obligatoria")
        @Size(max = 20, message = "La versión de la política no puede superar 20 caracteres")
        String policyVersion,

        @AssertTrue(message = "Debe aceptar el tratamiento de datos personales")
        boolean consentAccepted
) {

    /** Evita que la contraseña aparezca en logs si el objeto se imprime. */
    @Override
    public String toString() {
        return "TechnicianRegistrationRequest[email=" + email + "]";
    }
}
