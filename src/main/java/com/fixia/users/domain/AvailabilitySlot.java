package com.fixia.users.domain;

import com.fixia.users.exception.InvalidTechnicianDataException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

/**
 * Franja semanal en la que el técnico puede atender (RF-011): un día y un rango de horas en hora de Colombia
 * ({@link #ZONE}). Un día puede tener varias franjas (por ejemplo mañana y tarde). Las horas se guardan al minuto.
 */
public record AvailabilitySlot(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime)
        implements Comparable<AvailabilitySlot> {

    /**
     * Zona horaria en la que se interpretan las franjas. La solicitud de ms-services trae un instante
     * ({@code expected_at}); para compararlo con una franja se pasa a esta zona.
     */
    public static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private static final Comparator<AvailabilitySlot> ORDER = Comparator
            .comparing(AvailabilitySlot::dayOfWeek)
            .thenComparing(AvailabilitySlot::startTime)
            .thenComparing(AvailabilitySlot::endTime);

    public AvailabilitySlot {
        if (dayOfWeek == null || startTime == null || endTime == null) {
            throw new InvalidTechnicianDataException("La franja debe indicar día, hora de inicio y hora de fin");
        }
        startTime = startTime.truncatedTo(ChronoUnit.MINUTES);
        endTime = endTime.truncatedTo(ChronoUnit.MINUTES);
        if (!startTime.isBefore(endTime)) {
            throw new InvalidTechnicianDataException("La hora de inicio debe ser anterior a la hora de fin");
        }
    }

    /** Dos franjas seguidas (una termina cuando empieza la otra) no se cruzan. */
    public boolean overlaps(AvailabilitySlot other) {
        return dayOfWeek == other.dayOfWeek
                && startTime.isBefore(other.endTime)
                && other.startTime.isBefore(endTime);
    }

    /** Si el instante, en hora de Colombia, cae dentro de la franja (incluye el inicio, excluye el fin). */
    public boolean includes(Instant instant) {
        ZonedDateTime local = instant.atZone(ZONE);
        LocalTime time = local.toLocalTime();
        return local.getDayOfWeek() == dayOfWeek && !time.isBefore(startTime) && time.isBefore(endTime);
    }

    @Override
    public int compareTo(AvailabilitySlot other) {
        return ORDER.compare(this, other);
    }
}
