package com.fixia.users.dto;

import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.VerificationStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TechnicianProfileResponse(
        UUID technicianId,
        VerificationStatus verificationStatus,
        String professionalDescription,
        Short yearsOfExperience,
        Set<ServiceCategory> categories,
        Instant updatedAt
) {

    public static TechnicianProfileResponse from(Technician technician) {
        return new TechnicianProfileResponse(
                technician.getId(),
                technician.getVerificationStatus(),
                technician.getProfessionalDescription(),
                technician.getYearsOfExperience(),
                technician.getCategories(),
                technician.getUpdatedAt());
    }
}
