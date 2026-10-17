package com.fixia.users.controller;

import com.fixia.users.dto.EligibilityRequest;
import com.fixia.users.dto.EligibilityResponse;
import com.fixia.users.service.TechnicianEligibilityService;
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

    public InternalTechnicianController(TechnicianEligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
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
}
