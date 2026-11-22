package com.fixia.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Una vinculación o desvinculación de un técnico a una empresa (GC-524, RF-019). Solo se inserta: es el historial de la
 * asociación, que en sí misma vive en {@link CompanyTechnician} y se borra al desvincularse.
 */
@Entity
@Table(name = "company_membership_events")
public class CompanyMembershipEvent {

    public enum Type {
        /** El técnico aceptó una invitación y quedó en la empresa. */
        JOINED,
        /** El técnico se desvinculó de la empresa. */
        LEFT
    }

    @Id
    private UUID id;

    /** La fila de {@link CompanyTechnician} que nació o se borró: une un JOINED con su LEFT. */
    @Column(name = "membership_id", nullable = false, updatable = false)
    private UUID membershipId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "technician_user_id", nullable = false, updatable = false)
    private UUID technicianUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Type type;

    @Column(name = "invitation_id", updatable = false)
    private UUID invitationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected CompanyMembershipEvent() {
    }

    private CompanyMembershipEvent(CompanyTechnician membership, Type type, UUID invitationId, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.membershipId = membership.getId();
        this.companyId = membership.getCompanyId();
        this.technicianUserId = membership.getTechnicianUserId();
        this.type = type;
        this.invitationId = invitationId;
        this.occurredAt = occurredAt;
    }

    public static CompanyMembershipEvent joined(CompanyTechnician membership, UUID invitationId, Instant now) {
        if (invitationId == null) {
            throw new IllegalArgumentException("Una vinculación nace de una invitación");
        }
        return new CompanyMembershipEvent(membership, Type.JOINED, invitationId, now);
    }

    public static CompanyMembershipEvent left(CompanyTechnician membership, Instant now) {
        return new CompanyMembershipEvent(membership, Type.LEFT, null, now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMembershipId() {
        return membershipId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getTechnicianUserId() {
        return technicianUserId;
    }

    public Type getType() {
        return type;
    }

    public UUID getInvitationId() {
        return invitationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
