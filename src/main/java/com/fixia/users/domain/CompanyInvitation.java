package com.fixia.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Invitación de una {@link Company} a un técnico independiente (GC-523, RF-017). Solo la empresa que la creó la ve y la
 * cancela. La asociación real ({@link CompanyTechnician}) nace únicamente cuando el técnico acepta (GC-524).
 *
 * <p>Una sola invitación {@link InvitationStatus#PENDING} por empresa y técnico: la base lo garantiza con
 * {@code pending_marker}, que vale 1 solo mientras está pendiente (ver V12). La caducidad no se guarda: una pendiente
 * con {@code expiresAt} pasado se muestra como {@link InvitationDisplayStatus#EXPIRED} y ya no se puede cancelar ni
 * aceptar.
 */
@Entity
@Table(name = "company_invitations")
public class CompanyInvitation {

    private static final short PENDING_MARKER = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "technician_user_id", nullable = false)
    private UUID technicianUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    private InvitationCancelReason cancelReason;

    /** 1 mientras está pendiente y nulo después; sostiene la unicidad de la pendiente (V12). */
    @Column(name = "pending_marker")
    private Short pendingMarker;

    protected CompanyInvitation() {
    }

    public CompanyInvitation(UUID companyId, UUID technicianUserId, Instant now, Instant expiresAt) {
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("La invitación debe vencer después de crearse");
        }
        this.companyId = companyId;
        this.technicianUserId = technicianUserId;
        this.status = InvitationStatus.PENDING;
        this.pendingMarker = PENDING_MARKER;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    /** Pendiente cuyo plazo ya pasó. El instante de vencimiento exacto ya cuenta como vencida. */
    public boolean isExpired(Instant now) {
        return status == InvitationStatus.PENDING && !now.isBefore(expiresAt);
    }

    /** Pendiente y todavía dentro del plazo: la única que se puede cancelar (y, en GC-524, responder). */
    public boolean isOpen(Instant now) {
        return status == InvitationStatus.PENDING && now.isBefore(expiresAt);
    }

    public InvitationDisplayStatus displayStatus(Instant now) {
        if (isExpired(now) || (status == InvitationStatus.CANCELLED && cancelReason == InvitationCancelReason.EXPIRED)) {
            return InvitationDisplayStatus.EXPIRED;
        }
        return InvitationDisplayStatus.valueOf(status.name());
    }

    /** La empresa retira una invitación todavía abierta. Quien llama comprueba antes {@link #isOpen}. */
    public void cancelByCompany(Instant now) {
        close(InvitationStatus.CANCELLED, InvitationCancelReason.BY_COMPANY, now);
    }

    /** Se cierra una pendiente vencida para que la empresa pueda volver a invitar al mismo técnico. */
    public void closeAsExpired(Instant now) {
        close(InvitationStatus.CANCELLED, InvitationCancelReason.EXPIRED, now);
    }

    private void close(InvitationStatus newStatus, InvitationCancelReason reason, Instant now) {
        if (status != InvitationStatus.PENDING) {
            throw new IllegalStateException("Solo una invitación pendiente puede cambiar de estado");
        }
        this.status = newStatus;
        this.cancelReason = reason;
        this.pendingMarker = null;
        this.respondedAt = now;
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

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public InvitationCancelReason getCancelReason() {
        return cancelReason;
    }
}
