package com.fixia.users.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Una franja semanal en hora de Colombia; las horas van como {@code "08:00"}. */
public record AvailabilitySlotRequest(
        @NotNull(message = "El día es obligatorio")
        DayOfWeek dayOfWeek,

        @NotNull(message = "La hora de inicio es obligatoria")
        LocalTime startTime,

        @NotNull(message = "La hora de fin es obligatoria")
        LocalTime endTime
) {
}
