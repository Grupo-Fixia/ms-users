package com.fixia.users.dto;

import com.fixia.users.domain.CoverageZone;
import com.fixia.users.domain.Technician;

import java.time.Instant;

/** Zona de atención actual. Todos los campos de la zona son null mientras el técnico no la declare. */
public record CoverageZoneResponse(
        String city,
        Double latitude,
        Double longitude,
        Short radiusKm,
        Instant updatedAt
) {

    public static CoverageZoneResponse from(Technician technician) {
        CoverageZone zone = technician.getCoverageZone();
        if (zone == null) {
            return new CoverageZoneResponse(null, null, null, null, technician.getUpdatedAt());
        }
        return new CoverageZoneResponse(zone.getCity(), zone.getLatitude(), zone.getLongitude(), zone.getRadiusKm(),
                technician.getUpdatedAt());
    }
}
