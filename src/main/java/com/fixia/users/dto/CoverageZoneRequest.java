package com.fixia.users.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Zona de atención del técnico (RF-011): ciudad obligatoria y, si se quiere precisar, un círculo. Latitud, longitud
 * y radio van juntos o se omiten los tres. Reemplaza por completo la zona anterior.
 */
public record CoverageZoneRequest(
        @NotBlank(message = "La ciudad es obligatoria")
        @Size(max = 100, message = "La ciudad no puede superar 100 caracteres")
        String city,

        @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90")
        @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90")
        Double latitude,

        @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180")
        @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180")
        Double longitude,

        @Min(value = 1, message = "El radio debe estar entre 1 y 100 km")
        @Max(value = 100, message = "El radio debe estar entre 1 y 100 km")
        Integer radiusKm
) {
}
