package com.fixia.users.controller;

import com.fixia.users.dto.TechnicianAssociationRequest;
import com.fixia.users.dto.TechnicianAssociationResponse;
import com.fixia.users.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta interna de asociaciones empresa–técnico para ms-services (GC-468). Va bajo {@code /internal/**}, fuera de
 * Traefik y del Ingress, protegida por la clave compartida {@code X-Internal-Api-Key}.
 */
@RestController
@RequestMapping("/internal/companies")
public class InternalCompanyController {

    private final CompanyService companyService;

    public InternalCompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    /**
     * Siempre 200 con la misma forma: ningún caso de "no asociado" (cuentas inexistentes, deshabilitadas, de otro rol o
     * técnico de otra empresa) se distingue de otro. No se cachea: una cuenta deshabilitada invalida la respuesta.
     */
    @PostMapping("/technician-association")
    public ResponseEntity<TechnicianAssociationResponse> technicianAssociation(
            @Valid @RequestBody TechnicianAssociationRequest request) {
        boolean associated = companyService.isTechnicianAssociated(
                request.companyAdminUserId(), request.technicianUserId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new TechnicianAssociationResponse(associated));
    }
}
