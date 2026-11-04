package com.fixia.users.controller;

import com.fixia.users.domain.Role;
import com.fixia.users.dto.CreateEvaluationRequest;
import com.fixia.users.dto.EvaluationListResponse;
import com.fixia.users.dto.EvaluationResponse;
import com.fixia.users.exception.InvalidEvaluationQueryException;
import com.fixia.users.service.EvaluationService;
import com.fixia.users.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Evaluaciones de servicios cerrados (GC-444, contrato de GC-443). Para cliente y técnico; una empresa recibe 403
 * porque el flujo empresarial termina en la selección (RN-017). Quien evalúa y su rol salen siempre del token.
 */
@RestController
@RequestMapping("/api/users/evaluations")
public class EvaluationController {

    static final int MAX_SERVICES_PER_QUERY = 50;

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** 201 si se registró ahora; 200 con la existente si es un reintento idéntico. */
    @PostMapping
    public ResponseEntity<EvaluationResponse> register(@AuthenticationPrincipal Jwt jwt, Authentication authentication,
                                                       @Valid @RequestBody CreateEvaluationRequest request) {
        Role role = evaluatorRole(authentication);
        EvaluationService.Result result = evaluationService.register(parseUserId(jwt), role,
                request.serviceRequestId(), request.rating().intValueExact(), request.comment());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(EvaluationResponse.from(result.evaluation()));
    }

    /** {@code ?serviceRequestIds=a,b,c}, de 1 a 50 servicios. Los ajenos simplemente no aparecen. */
    @GetMapping
    public EvaluationListResponse list(@AuthenticationPrincipal Jwt jwt, Authentication authentication,
                                       @RequestParam(name = "serviceRequestIds", required = false)
                                       List<UUID> serviceRequestIds) {
        evaluatorRole(authentication);
        if (serviceRequestIds == null || serviceRequestIds.isEmpty() || serviceRequestIds.contains(null)) {
            throw new InvalidEvaluationQueryException("Debe indicar al menos un servicio");
        }
        LinkedHashSet<UUID> distinct = new LinkedHashSet<>(serviceRequestIds);
        if (distinct.size() > MAX_SERVICES_PER_QUERY) {
            throw new InvalidEvaluationQueryException(
                    "Se pueden consultar hasta " + MAX_SERVICES_PER_QUERY + " servicios a la vez");
        }
        return new EvaluationListResponse(evaluationService.visibleTo(parseUserId(jwt), distinct).stream()
                .map(EvaluationResponse::from)
                .toList());
    }

    private static Role evaluatorRole(Authentication authentication) {
        for (var authority : authentication.getAuthorities()) {
            if ("ROLE_CLIENT".equals(authority.getAuthority())) {
                return Role.CLIENT;
            }
            if ("ROLE_PROFESSIONAL".equals(authority.getAuthority())) {
                return Role.PROFESSIONAL;
            }
        }
        throw new AccessDeniedException("Su rol no permite evaluar servicios");
    }

    private static UUID parseUserId(Jwt jwt) {
        try {
            return TokenService.parseUserId(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new BadCredentialsException("Sesión no válida");
        }
    }
}
