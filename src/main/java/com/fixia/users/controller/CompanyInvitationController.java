package com.fixia.users.controller;

import com.fixia.users.domain.InvitationDisplayStatus;
import com.fixia.users.dto.CreateInvitationRequest;
import com.fixia.users.dto.InvitationResponse;
import com.fixia.users.dto.InvitationsResponse;
import com.fixia.users.service.CompanyInvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Invitaciones de la empresa autenticada a técnicos independientes (GC-523). Solo accesible con rol COMPANY (ver
 * SecurityConfig). La empresa se resuelve por el token, nunca por un id de la petición.
 */
@RestController
@RequestMapping("/api/users/companies/me/invitations")
public class CompanyInvitationController {

    private final CompanyInvitationService invitationService;

    public CompanyInvitationController(CompanyInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateInvitationRequest request) {
        return invitationService.invite(CompanyController.parseUserId(jwt), request.technicianEmail());
    }

    @GetMapping
    public InvitationsResponse list(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(required = false) InvitationDisplayStatus status) {
        return invitationService.list(CompanyController.parseUserId(jwt), status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        invitationService.cancel(CompanyController.parseUserId(jwt), id);
    }
}
