package com.fixia.users.dto;

import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.VerificationStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Perfil profesional propio. {@code reputation} (GC-446) es la que ven los clientes, o {@code null} si no tiene. */
public record TechnicianProfileResponse(
        UUID technicianId,
        VerificationStatus verificationStatus,
        String professionalDescription,
        Short yearsOfExperience,
        Set<ServiceCategory> categories,
        Instant updatedAt,
        Reputation reputation
) {

    public static TechnicianProfileResponse from(Technician technician, Reputation reputation) {
        return new TechnicianProfileResponse(
                technician.getId(),
                technician.getVerificationStatus(),
                technician.getProfessionalDescription(),
                technician.getYearsOfExperience(),
                technician.getCategories(),
                technician.getUpdatedAt(),
                reputation);
    }
}
