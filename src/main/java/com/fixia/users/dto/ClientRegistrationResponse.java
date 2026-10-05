package com.fixia.users.dto;

import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;

import java.time.Instant;
import java.util.UUID;

public record ClientRegistrationResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Role role,
        Instant createdAt
) {

    public static ClientRegistrationResponse from(User user) {
        return new ClientRegistrationResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getCreatedAt());
    }
}
