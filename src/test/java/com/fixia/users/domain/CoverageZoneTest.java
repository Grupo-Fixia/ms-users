package com.fixia.users.domain;

import com.fixia.users.exception.InvalidTechnicianDataException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverageZoneTest {

    /** Centro de Bogotá (Plaza de Bolívar) y un punto en Chapinero, a unos 4 km. */
    private static final double CENTER_LAT = 4.598;
    private static final double CENTER_LON = -74.076;
    private static final double CHAPINERO_LAT = 4.63;
    private static final double CHAPINERO_LON = -74.06;

    @Test
    void unaZonaPuedeSerSoloUnaCiudad() {
        CoverageZone zone = new CoverageZone("  Bogotá  ");

        assertThat(zone.getCity()).isEqualTo("Bogotá");
        assertThat(zone.hasCircle()).isFalse();
        assertThat(zone.getLatitude()).isNull();
        assertThat(zone.getRadiusKm()).isNull();
    }

    @Test
    void elCirculoGuardaLasCoordenadasConDosDecimalesComoLaSolicitud() {
        CoverageZone zone = new CoverageZone("Bogotá", 4.653281, -74.083649, 15);

        assertThat(zone.hasCircle()).isTrue();
        assertThat(zone.getLatitude()).isEqualTo(4.65);
        assertThat(zone.getLongitude()).isEqualTo(-74.08);
        assertThat(zone.getRadiusKm()).isEqualTo((short) 15);
    }

    @Test
    void rechazaCiudadAusenteOLarga() {
        assertThatThrownBy(() -> new CoverageZone(null)).isInstanceOf(InvalidTechnicianDataException.class);
        assertThatThrownBy(() -> new CoverageZone("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverageZone("x".repeat(101))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new CoverageZone("x".repeat(100)).getCity()).hasSize(100);
    }

    @Test
    void rechazaUnCirculoFueraDeRango() {
        assertThatThrownBy(() -> new CoverageZone("Bogotá", 90.5, 0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverageZone("Bogotá", 0, -180.5, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverageZone("Bogotá", 4.6, -74.1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverageZone("Bogotá", 4.6, -74.1, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new CoverageZone("Polo", -90, 180, 100).getRadiusKm()).isEqualTo((short) 100);
    }

    @Test
    void cubreUnaSolicitudDeLaMismaCiudadSinImportarMayusculasNiTildes() {
        CoverageZone zone = new CoverageZone("Bogotá");

        assertThat(zone.covers("bogota", null, null)).isTrue();
        assertThat(zone.covers(" BOGOTÁ ", CHAPINERO_LAT, CHAPINERO_LON)).isTrue();
        assertThat(zone.covers("Medellín", null, null)).isFalse();
        assertThat(zone.covers(null, null, null)).isFalse();
    }

    @Test
    void conCirculoExigeQueElPuntoCaigaDentroDelRadio() {
        CoverageZone fiveKm = new CoverageZone("Bogotá", CENTER_LAT, CENTER_LON, 5);
        CoverageZone twoKm = new CoverageZone("Bogotá", CENTER_LAT, CENTER_LON, 2);

        assertThat(fiveKm.covers("Bogotá", CHAPINERO_LAT, CHAPINERO_LON)).isTrue();
        assertThat(twoKm.covers("Bogotá", CHAPINERO_LAT, CHAPINERO_LON)).isFalse();
        assertThat(fiveKm.covers("Medellín", CHAPINERO_LAT, CHAPINERO_LON)).isFalse();
    }

    @Test
    void siLaSolicitudNoTraeCoordenadasBastaLaCiudad() {
        CoverageZone twoKm = new CoverageZone("Bogotá", CENTER_LAT, CENTER_LON, 2);

        assertThat(twoKm.covers("Bogotá", null, null)).isTrue();
        assertThat(twoKm.covers("Bogotá", CHAPINERO_LAT, null)).isTrue();
    }

    @Test
    void dosZonasConLosMismosValoresSonIguales() {
        assertThat(new CoverageZone("Bogotá", 4.651, -74.08, 10))
                .isEqualTo(new CoverageZone("Bogotá", 4.649, -74.08, 10))
                .hasSameHashCodeAs(new CoverageZone("Bogotá", 4.649, -74.08, 10))
                .isNotEqualTo(new CoverageZone("Bogotá", 4.65, -74.08, 11))
                .isNotEqualTo(new CoverageZone("Bogotá"))
                .isNotEqualTo("Bogotá");
        assertThat(new CoverageZone("Bogotá")).isEqualTo(new CoverageZone("Bogotá"))
                .isNotEqualTo(new CoverageZone("Cali"));
    }
}
