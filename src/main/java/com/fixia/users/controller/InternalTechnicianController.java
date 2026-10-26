package com.fixia.users.controller;

import com.fixia.users.dto.EligibilityRequest;
import com.fixia.users.dto.EligibilityResponse;
import com.fixia.users.dto.PublicProfilesRequest;
import com.fixia.users.dto.PublicProfilesResponse;
import com.fixia.users.service.TechnicianEligibilityService;
import com.fixia.users.service.TechnicianPublicProfileService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Consulta interna de elegibilidad para otros microservicios (ms-services, GC-342 y GC-337).
 *
 * <p>Ruta fuera de {@code /api/users}: ni Traefik ni el Ingress la exponen. Va bajo {@code /internal/**} para que la
 * proteja la clave compartida {@code X-Internal-Api-Key} que agrega el PR de la consulta interna de revocación
 * (GC-333); hasta que ese cambio esté en la rama, la ruta exige una sesión como cualquier otra.
 */
@RestController
@RequestMapping("/internal/technicians")
public class InternalTechnicianController {

    private final TechnicianEligibilityService eligibilityService;
    private final TechnicianPublicProfileService publicProfileService;

    public InternalTechnicianController(TechnicianEligibilityService eligibilityService,
                                        TechnicianPublicProfileService publicProfileService) {
        this.eligibilityService = eligibilityService;
        this.publicProfileService = publicProfileService;
    }

    /**
     * Un técnico inexistente o deshabilitado no da 404: cada solicitud responde no elegible con
     * {@code TECHNICIAN_NOT_FOUND}, y así la respuesta tiene siempre la misma forma. No debe cachearse: un cambio de
     * zona o disponibilidad la invalida.
     */
    @PostMapping("/{userId}/eligibility")
    public ResponseEntity<EligibilityResponse> eligibility(@PathVariable UUID userId,
                                                           @Valid @RequestBody EligibilityRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(eligibilityService.evaluate(userId, request.requests()));
    }

    /**
     * Perfil público de varios técnicos a la vez (GC-425), para que ms-services le muestre al cliente quién se
     * postuló. Los ids que no son de un técnico activo se omiten de la respuesta en lugar de dar 404. No se cachea:
     * el técnico puede cambiar su perfil en cualquier momento.
     */
    @PostMapping("/public-profiles")
    public ResponseEntity<PublicProfilesResponse> publicProfiles(@Valid @RequestBody PublicProfilesRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(publicProfileService.find(request.userIds()));
    }
}
