package com.fixia.users.controller;

import com.fixia.users.dto.CompanyPublicProfilesRequest;
import com.fixia.users.dto.CompanyPublicProfilesResponse;
import com.fixia.users.dto.CompanyTechnicianIdsResponse;
import com.fixia.users.dto.CompanyTechniciansQuery;
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

    /**
     * Técnicos activos de la empresa que administra esta cuenta (GC-468), para que ms-services calcule qué solicitudes
     * puede atender la empresa y con cuál de sus técnicos. Siempre 200: una cuenta que no es de una empresa activa
     * responde lista vacía, igual que una empresa sin técnicos. No se cachea.
     */
    @PostMapping("/technicians")
    public ResponseEntity<CompanyTechnicianIdsResponse> technicians(@Valid @RequestBody CompanyTechniciansQuery query) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(companyService.technicianIds(query.companyAdminUserId()));
    }

    /**
     * Nombre de varias empresas a la vez (GC-468), para que ms-services le muestre al cliente quién se postuló. Las
     * cuentas que no son de una empresa activa se omiten en lugar de dar 404. No se cachea.
     */
    @PostMapping("/public-profiles")
    public ResponseEntity<CompanyPublicProfilesResponse> publicProfiles(
            @Valid @RequestBody CompanyPublicProfilesRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(companyService.publicProfiles(request.companyAdminUserIds()));
    }
}
