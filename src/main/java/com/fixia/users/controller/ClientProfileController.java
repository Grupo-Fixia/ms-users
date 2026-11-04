package com.fixia.users.controller;

import com.fixia.users.dto.ClientProfileRequest;
import com.fixia.users.dto.ClientProfileResponse;
import com.fixia.users.service.ClientProfileService;
import com.fixia.users.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Perfil del cliente autenticado (GC-317). Solo accesible con rol CLIENT (ver SecurityConfig). */
@RestController
@RequestMapping("/api/users/clients/me/profile")
public class ClientProfileController {

    private final ClientProfileService profileService;

    public ClientProfileController(ClientProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ClientProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return ClientProfileResponse.from(profileService.get(parseUserId(jwt)));
    }

    @PutMapping
    public ClientProfileResponse update(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody ClientProfileRequest request) {
        return ClientProfileResponse.from(profileService.update(parseUserId(jwt), request));
    }

    private static UUID parseUserId(Jwt jwt) {
        try {
            return TokenService.parseUserId(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new BadCredentialsException("Sesión no válida");
        }
    }
}
