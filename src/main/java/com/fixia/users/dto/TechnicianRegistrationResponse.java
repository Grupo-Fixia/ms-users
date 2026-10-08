package com.fixia.users.dto;

import com.fixia.users.domain.Role;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.domain.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

public record TechnicianRegistrationResponse(
        UUID id,
        UUID technicianId,
        String email,
        String firstName,
        String lastName,
        Role role,
        VerificationStatus verificationStatus,
        Instant createdAt
) {

    public static TechnicianRegistrationResponse from(User user, Technician technician) {
        return new TechnicianRegistrationResponse(
                user.getId(),
                technician.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                technician.getVerificationStatus(),
                user.getCreatedAt());
    }
}
