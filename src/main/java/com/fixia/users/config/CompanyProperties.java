package com.fixia.users.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Reglas configurables de las empresas (GC-523).
 *
 * @param invitationTtl cuánto dura una invitación: 7 días por defecto (regla 6 del PO, docs/contracts/empresas.md); se
 *                      cambia con {@code FIXIA_COMPANIES_INVITATION_TTL} (por ejemplo {@code P3D})
 */
@ConfigurationProperties(prefix = "fixia.companies")
public record CompanyProperties(@DefaultValue("P7D") Duration invitationTtl) {

    public CompanyProperties {
        if (invitationTtl == null || invitationTtl.isZero() || invitationTtl.isNegative()) {
            throw new IllegalArgumentException("fixia.companies.invitation-ttl debe ser una duración positiva");
        }
    }
}
