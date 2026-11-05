package com.fixia.users.controller;

import com.fixia.users.dto.TechnicianProfileRequest;
import com.fixia.users.dto.TechnicianProfileResponse;
import com.fixia.users.domain.Technician;
import com.fixia.users.service.ReputationService;
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

/** Perfil profesional del técnico autenticado. Solo accesible con rol PROFESSIONAL (ver SecurityConfig). */
@RestController
@RequestMapping("/api/users/technicians/me/profile")
public class TechnicianProfileController {

    private final TechnicianProfileService profileService;
    private final ReputationService reputationService;

    public TechnicianProfileController(TechnicianProfileService profileService, ReputationService reputationService) {
        this.profileService = profileService;
        this.reputationService = reputationService;
    }

    @GetMapping
    public TechnicianProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return response(profileService.get(parseUserId(jwt)));
    }

    @PutMapping
    public TechnicianProfileResponse update(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody TechnicianProfileRequest request) {
        return response(profileService.update(parseUserId(jwt), request));
    }

    /** Con la reputación que ven los clientes (GC-446), calculada de sus evaluaciones. */
    private TechnicianProfileResponse response(Technician technician) {
        return TechnicianProfileResponse.from(technician, reputationService.asProvider(technician.getUserId()));
    }

    private static UUID parseUserId(Jwt jwt) {
        try {
            return TokenService.parseUserId(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new BadCredentialsException("Sesión no válida");
        }
    }
}
