package com.fixia.users.controller;

import com.fixia.users.dto.MembershipResponse;
import com.fixia.users.dto.ReceivedInvitationResponse;
import com.fixia.users.service.TechnicianMembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * El técnico ante las invitaciones empresariales y su empresa (GC-524, contrato de empresas §5). Solo con rol
 * PROFESSIONAL (ver SecurityConfig, {@code /api/users/technicians/me/**}). El técnico sale del token.
 */
@RestController
@RequestMapping("/api/users/technicians/me")
public class TechnicianMembershipController {

    private final TechnicianMembershipService membershipService;

    public TechnicianMembershipController(TechnicianMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping("/invitations")
    public ReceivedInvitationResponse.Page invitations(@AuthenticationPrincipal Jwt jwt) {
        return membershipService.received(CompanyController.parseUserId(jwt));
    }

    @PostMapping("/invitations/{id}/accept")
    public MembershipResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return membershipService.accept(CompanyController.parseUserId(jwt), id);
    }

    @PostMapping("/invitations/{id}/reject")
    public ReceivedInvitationResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return membershipService.reject(CompanyController.parseUserId(jwt), id);
    }

    @GetMapping("/company")
    public MembershipResponse company(@AuthenticationPrincipal Jwt jwt) {
        return membershipService.company(CompanyController.parseUserId(jwt));
    }

    @DeleteMapping("/company")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal Jwt jwt) {
        membershipService.leave(CompanyController.parseUserId(jwt));
    }
}
