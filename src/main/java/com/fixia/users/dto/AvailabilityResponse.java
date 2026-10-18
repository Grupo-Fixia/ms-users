package com.fixia.users.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fixia.users.domain.AvailabilitySlot;
import com.fixia.users.domain.Technician;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** Disponibilidad actual, ordenada por día y hora de inicio, con la zona horaria en que se leen las horas. */
public record AvailabilityResponse(
        List<Slot> slots,
        String timeZone,
        Instant updatedAt
) {

    /** Las horas salen como "HH:mm", el mismo formato con el que se envían. */
    public record Slot(DayOfWeek dayOfWeek,
                       @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                       @JsonFormat(pattern = "HH:mm") LocalTime endTime) {

        static Slot from(AvailabilitySlot slot) {
            return new Slot(slot.dayOfWeek(), slot.startTime(), slot.endTime());
        }
    }

    public static AvailabilityResponse from(Technician technician) {
        return new AvailabilityResponse(
                technician.getAvailability().stream().map(Slot::from).toList(),
                AvailabilitySlot.ZONE.getId(),
                technician.getUpdatedAt());
    }
}
