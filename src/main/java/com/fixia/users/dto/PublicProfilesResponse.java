package com.fixia.users.dto;

import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.domain.VerificationStatus;

import java.util.List;
import java.util.UUID;

/**
 * Perfiles públicos de los técnicos consultados, en el orden pedido. Un técnico que no existe, cuya cuenta está
 * deshabilitada o que no es técnico no aparece: quien consulta no recibe datos inventados.
 */
public record PublicProfilesResponse(List<Profile> profiles) {

    /**
     * Solo lo que el cliente necesita para comparar postulantes (GC-425, DEC-S4-07): nombre corto, verificación,
     * especialidades, descripción, experiencia y ciudad. Nunca correo, teléfono, documento ni el centro y el radio de
     * la zona de atención, que pueden revelar el domicilio. Los campos que el técnico no ha llenado llegan en
     * {@code null} (las especialidades, como lista vacía).
     */
    public record Profile(
            UUID userId,
            String displayName,
            VerificationStatus verificationStatus,
            List<ServiceCategory> categories,
            String professionalDescription,
            Short yearsOfExperience,
            String city
    ) {
    }
}
