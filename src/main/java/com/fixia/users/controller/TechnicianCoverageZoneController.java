package com.fixia.users.controller;

import com.fixia.users.dto.CoverageZoneRequest;
import com.fixia.users.dto.CoverageZoneResponse;
import com.fixia.users.service.TechnicianProfileService;
import com.fixia.users.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Zona de atención del técnico autenticado (RF-011). Solo accesible con rol PROFESSIONAL (ver SecurityConfig). */
@RestController
@RequestMapping("/api/users/technicians/me/coverage-zone")
public class TechnicianCoverageZoneController {

    private final TechnicianProfileService profileService;

    public TechnicianCoverageZoneController(TechnicianProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public CoverageZoneResponse get(@AuthenticationPrincipal Jwt jwt) {
        return CoverageZoneResponse.from(profileService.get(parseUserId(jwt)));
    }

    @PutMapping
    public CoverageZoneResponse update(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody CoverageZoneRequest request) {
        return CoverageZoneResponse.from(profileService.updateCoverageZone(parseUserId(jwt), request));
    }

    /** Retira la zona de atención. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        profileService.clearCoverageZone(parseUserId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static UUID parseUserId(Jwt jwt) {
        try {
            return TokenService.parseUserId(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new BadCredentialsException("Sesión no válida");
        }
    }
}
