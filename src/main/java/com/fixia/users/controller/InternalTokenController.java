package com.fixia.users.controller;

import com.fixia.users.dto.TokenStatusResponse;
import com.fixia.users.repository.RevokedTokenRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta interna de revocación para otros microservicios (por ejemplo ms-services). La lista de tokens
 * revocados vive en la base de este servicio (Database per Service), así que los demás la consultan aquí.
 *
 * <p>Ruta fuera de {@code /api/users}: ni Traefik ni el Ingress la exponen, y además exige la cabecera
 * {@code X-Internal-Api-Key} (ver {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/internal/tokens")
public class InternalTokenController {

    private final RevokedTokenRepository revokedTokenRepository;

    public InternalTokenController(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    /**
     * Un {@code jti} desconocido responde {@code revoked=false} (200) y no 404: la respuesta no sirve para
     * averiguar qué identificadores existen. La respuesta no debe cachearse, porque un logout la invalida.
     */
    @GetMapping("/{jti}/status")
    public ResponseEntity<TokenStatusResponse> status(@PathVariable String jti) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new TokenStatusResponse(revokedTokenRepository.existsById(jti)));
    }
}
