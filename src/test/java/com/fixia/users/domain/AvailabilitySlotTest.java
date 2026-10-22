package com.fixia.users.domain;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailabilitySlotTest {

    private static AvailabilitySlot slot(DayOfWeek day, String start, String end) {
        return new AvailabilitySlot(day, LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void guardaLasHorasAlMinuto() {
        AvailabilitySlot slot = slot(DayOfWeek.MONDAY, "08:00:45.500", "12:30:59");

        assertThat(slot.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(slot.endTime()).isEqualTo(LocalTime.of(12, 30));
    }

    @Test
    void rechazaUnaFranjaIncompletaOQueNoAvanza() {
        assertThatThrownBy(() -> slot(DayOfWeek.MONDAY, "12:00", "08:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> slot(DayOfWeek.MONDAY, "08:00", "08:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> slot(DayOfWeek.MONDAY, "08:00:10", "08:00:50"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AvailabilitySlot(null, LocalTime.of(8, 0), LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AvailabilitySlot(DayOfWeek.MONDAY, null, LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AvailabilitySlot(DayOfWeek.MONDAY, LocalTime.of(8, 0), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dosFranjasSeCruzanSoloSiCompartenTiempoDelMismoDia() {
        AvailabilitySlot morning = slot(DayOfWeek.MONDAY, "08:00", "12:00");

        assertThat(morning.overlaps(slot(DayOfWeek.MONDAY, "11:00", "14:00"))).isTrue();
        assertThat(morning.overlaps(slot(DayOfWeek.MONDAY, "09:00", "10:00"))).isTrue();
        assertThat(morning.overlaps(slot(DayOfWeek.MONDAY, "12:00", "14:00"))).isFalse();
        assertThat(morning.overlaps(slot(DayOfWeek.MONDAY, "06:00", "08:00"))).isFalse();
        assertThat(morning.overlaps(slot(DayOfWeek.TUESDAY, "08:00", "12:00"))).isFalse();
    }

    @Test
    void unInstanteSeComparaEnHoraDeColombia() {
        AvailabilitySlot mondayMorning = slot(DayOfWeek.MONDAY, "08:00", "12:00");

        // 2026-10-19 es lunes. 13:00 UTC son las 08:00 en Bogotá (UTC-5).
        assertThat(mondayMorning.includes(Instant.parse("2026-10-19T13:00:00Z"))).isTrue();
        assertThat(mondayMorning.includes(Instant.parse("2026-10-19T16:59:00Z"))).isTrue();
        assertThat(mondayMorning.includes(Instant.parse("2026-10-19T17:00:00Z"))).isFalse();
        assertThat(mondayMorning.includes(Instant.parse("2026-10-19T12:59:00Z"))).isFalse();
        // El lunes a las 03:00 UTC todavía es domingo en Bogotá (22:00).
        assertThat(slot(DayOfWeek.SUNDAY, "21:00", "23:00").includes(Instant.parse("2026-10-19T03:00:00Z"))).isTrue();
        assertThat(mondayMorning.includes(Instant.parse("2026-10-20T14:00:00Z"))).isFalse();
    }

    @Test
    void seOrdenanPorDiaYHoraDeInicio() {
        AvailabilitySlot tuesday = slot(DayOfWeek.TUESDAY, "07:00", "09:00");
        AvailabilitySlot mondayAfternoon = slot(DayOfWeek.MONDAY, "14:00", "18:00");
        AvailabilitySlot mondayMorning = slot(DayOfWeek.MONDAY, "08:00", "12:00");

        List<AvailabilitySlot> sorted = Stream.of(tuesday, mondayAfternoon, mondayMorning).sorted().toList();

        assertThat(sorted).containsExactly(mondayMorning, mondayAfternoon, tuesday);
    }
}
