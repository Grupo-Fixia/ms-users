package com.fixia.users.controller;

import com.fixia.users.dto.AvailabilityRequest;
import com.fixia.users.dto.AvailabilityResponse;
import com.fixia.users.service.TechnicianProfileService;
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

/** Disponibilidad semanal del técnico autenticado (RF-011). Solo accesible con rol PROFESSIONAL (ver SecurityConfig). */
@RestController
@RequestMapping("/api/users/technicians/me/availability")
public class TechnicianAvailabilityController {

    private final TechnicianProfileService profileService;

    public TechnicianAvailabilityController(TechnicianProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public AvailabilityResponse get(@AuthenticationPrincipal Jwt jwt) {
        return AvailabilityResponse.from(profileService.get(parseUserId(jwt)));
    }

    @PutMapping
    public AvailabilityResponse update(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody AvailabilityRequest request) {
        return AvailabilityResponse.from(profileService.replaceAvailability(parseUserId(jwt), request.slots()));
    }

    private static UUID parseUserId(Jwt jwt) {
        try {
            return TokenService.parseUserId(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new BadCredentialsException("Sesión no válida");
        }
    }
}
