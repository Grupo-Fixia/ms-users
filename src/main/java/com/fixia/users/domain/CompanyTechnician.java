package com.fixia.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Asociación persistente entre una {@link Company} y la cuenta de un técnico existente (GC-466). Un técnico solo puede
 * estar asociado a una empresa; la restricción vive en la base y no solo en el código, para que dos altas simultáneas
 * no la burlen. No hay indicador de habilitación: si la cuenta del técnico se deshabilita, deja de aparecer en las
 * consultas por su estado en {@link User}.
 */
@Entity
@Table(name = "company_technicians")
public class CompanyTechnician {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "technician_user_id", nullable = false, unique = true)
    private UUID technicianUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CompanyTechnician() {
    }

    public CompanyTechnician(UUID companyId, UUID technicianUserId, Instant now) {
        this.companyId = companyId;
        this.technicianUserId = technicianUserId;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getTechnicianUserId() {
        return technicianUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
