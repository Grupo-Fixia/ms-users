package com.fixia.users.dto;

import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Perfil del cliente autenticado (GC-317): sus datos de cuenta. Nunca incluye el hash de la contraseña, el rol, el
 * estado ni el consentimiento. El correo y el documento se muestran pero no se pueden editar.
 */
public record ClientProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        DocumentType documentType,
        String documentNumber,
        String phone,
        Instant createdAt
) {
    public static ClientProfileResponse from(User user) {
        return new ClientProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDocumentType(),
                user.getDocumentNumber(),
                user.getPhone(),
                user.getCreatedAt());
    }

    /** Evita que datos personales aparezcan en logs si el objeto se imprime. */
    @Override
    public String toString() {
        return "ClientProfileResponse[id=" + id + "]";
    }
}
