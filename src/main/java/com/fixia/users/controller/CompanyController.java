package com.fixia.users.controller;

import com.fixia.users.dto.CompanyResponse;
import com.fixia.users.dto.CompanyTechniciansResponse;
import com.fixia.users.service.CompanyService;
import com.fixia.users.service.TokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Empresa del administrador autenticado (GC-466). Solo accesible con rol COMPANY (ver SecurityConfig). */
@RestController
@RequestMapping("/api/users/companies/me")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public CompanyResponse get(@AuthenticationPrincipal Jwt jwt) {
        return CompanyResponse.from(companyService.get(parseUserId(jwt)));
    }

    @GetMapping("/technicians")
    public CompanyTechniciansResponse technicians(@AuthenticationPrincipal Jwt jwt) {
        return companyService.listTechnicians(parseUserId(jwt));
    }

    private static UUID parseUserId(Jwt jwt) {
        try {
            return TokenService.parseUserId(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new BadCredentialsException("Sesión no válida");
        }
    }
}
