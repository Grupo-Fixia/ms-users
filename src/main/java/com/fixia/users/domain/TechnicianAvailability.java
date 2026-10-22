package com.fixia.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/** Franja de disponibilidad declarada por un técnico (RF-011). */
@Entity
@Table(name = "technician_availability")
public class TechnicianAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false)
    private Technician technician;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected TechnicianAvailability() {
    }

    TechnicianAvailability(Technician technician, AvailabilitySlot slot) {
        this.technician = technician;
        this.dayOfWeek = slot.dayOfWeek();
        this.startTime = slot.startTime();
        this.endTime = slot.endTime();
    }

    public AvailabilitySlot getSlot() {
        return new AvailabilitySlot(dayOfWeek, startTime, endTime);
    }
}
