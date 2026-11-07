package com.fixia.users.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Una acción del ADMIN de Fixia sobre datos de ms-users (GC-535, contrato {@code docs/contracts/admin.md} §5). Es
 * inmutable: no tiene setters y ninguna ruta la edita ni la borra.
 */
@Entity
@Table(name = "admin_audit_events")
public class AdminAuditEvent {

    /** Sobre qué tipo de recurso se actuó; en ms-users solo hay usuarios. */
    public static final String TARGET_USER = "USER";

    @Id
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminAction action;

    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminAuditOutcome outcome;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AdminAuditEvent() {
    }

    public AdminAuditEvent(UUID adminId, AdminAction action, UUID targetUserId, String details,
                           AdminAuditOutcome outcome, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetUserId == null ? null : TARGET_USER;
        this.targetId = targetUserId;
        this.details = details;
        this.outcome = outcome;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public AdminAction getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getDetails() {
        return details;
    }

    public AdminAuditOutcome getOutcome() {
        return outcome;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
