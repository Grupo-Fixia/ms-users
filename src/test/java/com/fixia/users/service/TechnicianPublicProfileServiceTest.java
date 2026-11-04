package com.fixia.users.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Nombre corto del perfil público (GC-425): nombre e inicial del apellido. */
class TechnicianPublicProfileServiceTest {

    @Test
    void muestraElNombreYLaInicialDelApellido() {
        assertThat(TechnicianPublicProfileService.displayName("Carlos", "Gómez")).isEqualTo("Carlos G.");
        assertThat(TechnicianPublicProfileService.displayName(" María José ", " Ñúñez Torres "))
                .isEqualTo("María José Ñ.");
    }

    @Test
    void toleraNombresVaciosSinInventarNada() {
        assertThat(TechnicianPublicProfileService.displayName("Carlos", "  ")).isEqualTo("Carlos");
        assertThat(TechnicianPublicProfileService.displayName(null, "Gómez")).isEqualTo("G.");
        assertThat(TechnicianPublicProfileService.displayName(null, null)).isEmpty();
    }
}
