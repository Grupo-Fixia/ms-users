package com.fixia.users.controller;

import com.fixia.users.dto.TechnicianCategoriesRequest;
import com.fixia.users.dto.TechnicianCategoriesResponse;
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

/**
 * Especialidades y servicios ofrecidos por el técnico autenticado (RF-010). Solo accesible con rol PROFESSIONAL
 * (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/users/technicians/me/categories")
public class TechnicianCategoriesController {

    private final TechnicianProfileService profileService;

    public TechnicianCategoriesController(TechnicianProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public TechnicianCategoriesResponse get(@AuthenticationPrincipal Jwt jwt) {
        return TechnicianCategoriesResponse.from(profileService.get(parseUserId(jwt)));
    }

    @PutMapping
    public TechnicianCategoriesResponse update(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody TechnicianCategoriesRequest request) {
        return TechnicianCategoriesResponse.from(
                profileService.updateCategories(parseUserId(jwt), request.categories()));
    }

    private static UUID parseUserId(Jwt jwt) {
        try {
            return TokenService.parseUserId(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new BadCredentialsException("Sesión no válida");
        }
    }
}
