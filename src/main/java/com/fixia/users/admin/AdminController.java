package com.fixia.users.admin;

import com.fixia.users.service.TokenService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Consultas del ADMIN de Fixia en ms-users (GC-535, contrato {@code docs/contracts/admin.md} §3). Solo {@code ROLE_ADMIN}
 * (ver SecurityConfig): sin token 401, otro rol 403. Quien consulta sale siempre del token.
 */
@RestController
@RequestMapping("/api/users/admin")
public class AdminController {

    private final AdminUserService users;
    private final AdminAuditQueryService auditEvents;

    public AdminController(AdminUserService users, AdminAuditQueryService auditEvents) {
        this.users = users;
        this.auditEvents = auditEvents;
    }

    @GetMapping("/users")
    public AdminResponses.UserPage listUsers(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(required = false) String role,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(required = false) Integer limit,
                                             @RequestParam(required = false) String cursor) {
        return users.list(adminId(jwt), role, status, q, limit, cursor);
    }

    @GetMapping("/users/{id}")
    public AdminResponses.UserDetail user(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return users.detail(adminId(jwt), id);
    }

    @GetMapping("/audit-events")
    public AdminResponses.AuditPage listAuditEvents(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestParam(required = false) UUID adminId,
                                                    @RequestParam(required = false) String action,
                                                    @RequestParam(required = false) Integer limit,
                                                    @RequestParam(required = false) String cursor) {
        return auditEvents.list(adminId(jwt), adminId, action, limit, cursor);
    }

    private static UUID adminId(Jwt jwt) {
        return TokenService.parseUserId(jwt.getSubject());
    }
}
