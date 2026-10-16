package com.fixia.users.domain;

import com.fixia.users.exception.InvalidTechnicianDataException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * Zona de atención del técnico (RF-011), con la misma forma que la ubicación aproximada de una solicitud en
 * ms-services: una ciudad obligatoria y, si el técnico quiere precisarla, un círculo (centro y radio). Las
 * coordenadas se guardan redondeadas a dos decimales (~1 km), igual que las de la solicitud, para no revelar
 * dónde vive el técnico.
 */
@Embeddable
public class CoverageZone {

    public static final int CITY_MAX_LENGTH = 100;
    public static final int MIN_RADIUS_KM = 1;
    public static final int MAX_RADIUS_KM = 100;

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Column(name = "coverage_city", length = CITY_MAX_LENGTH)
    private String city;

    @Column(name = "coverage_latitude")
    private Double latitude;

    @Column(name = "coverage_longitude")
    private Double longitude;

    @Column(name = "coverage_radius_km")
    private Short radiusKm;

    protected CoverageZone() {
    }

    /** Zona de toda una ciudad, sin círculo. */
    public CoverageZone(String city) {
        this.city = requireCity(city);
    }

    /** Zona de una ciudad precisada con un círculo de centro (latitud, longitud) y radio en km. */
    public CoverageZone(String city, double latitude, double longitude, int radiusKm) {
        this(city);
        if (Math.abs(latitude) > 90) {
            throw new InvalidTechnicianDataException("La latitud debe estar entre -90 y 90");
        }
        if (Math.abs(longitude) > 180) {
            throw new InvalidTechnicianDataException("La longitud debe estar entre -180 y 180");
        }
        if (radiusKm < MIN_RADIUS_KM || radiusKm > MAX_RADIUS_KM) {
            throw new InvalidTechnicianDataException(
                    "El radio debe estar entre " + MIN_RADIUS_KM + " y " + MAX_RADIUS_KM + " km");
        }
        this.latitude = round(latitude);
        this.longitude = round(longitude);
        this.radiusKm = (short) radiusKm;
    }

    private static String requireCity(String city) {
        if (city == null || city.isBlank()) {
            throw new InvalidTechnicianDataException("La ciudad es obligatoria");
        }
        String stripped = city.strip();
        if (stripped.length() > CITY_MAX_LENGTH) {
            throw new InvalidTechnicianDataException("La ciudad no puede superar " + CITY_MAX_LENGTH + " caracteres");
        }
        return stripped;
    }

    /** Mismo redondeo que ms-services aplica a la ubicación aproximada de la solicitud. */
    private static double round(double coordinate) {
        return Math.round(coordinate * 100.0) / 100.0;
    }

    /**
     * Regla de compatibilidad por ubicación (para GC-337), sobre la ubicación aproximada de una solicitud: la
     * ciudad debe coincidir (sin distinguir mayúsculas, tildes ni espacios sobrantes) y, si el técnico definió
     * un círculo y la solicitud trae coordenadas, el punto debe caer dentro del radio. Si falta alguno de los
     * dos, basta la ciudad.
     */
    public boolean covers(String requestCity, Double requestLatitude, Double requestLongitude) {
        if (requestCity == null || !normalize(city).equals(normalize(requestCity))) {
            return false;
        }
        if (!hasCircle() || requestLatitude == null || requestLongitude == null) {
            return true;
        }
        return distanceKm(latitude, longitude, requestLatitude, requestLongitude) <= radiusKm;
    }

    private static String normalize(String value) {
        String withoutAccents = Normalizer.normalize(value.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT);
    }

    /** Distancia sobre la esfera terrestre (fórmula del haversine). */
    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    public boolean hasCircle() {
        return radiusKm != null;
    }

    public String getCity() {
        return city;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Short getRadiusKm() {
        return radiusKm;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CoverageZone zone
                && city.equals(zone.city)
                && Objects.equals(latitude, zone.latitude)
                && Objects.equals(longitude, zone.longitude)
                && Objects.equals(radiusKm, zone.radiusKm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(city, latitude, longitude, radiusKm);
    }
}
