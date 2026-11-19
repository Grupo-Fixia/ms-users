package com.fixia.users.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.domain.VerificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respuestas de las consultas administrativas de ms-users (contrato {@code docs/contracts/admin.md} §3). */
public final class AdminResponses {

    private AdminResponses() {
    }

    /** Usuario en la lista: sin documento ni teléfono (§3.1). */
    public record UserSummary(UUID id, String email, String firstName, String lastName, Role role,
                              UserStatus status, Instant createdAt) {
    }

    /** Detalle de un usuario (§3.2). El documento va enmascarado; nunca la contraseña. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserDetail(UUID id, String email, String firstName, String lastName, Role role,
                             UserStatus status, Instant createdAt, String phone, DocumentType documentType,
                             String documentNumberMasked, VerificationStatus verificationStatus,
                             String companyName) {
    }

    public record UserPage(List<UserSummary> items, String nextCursor) {
    }

    /** Una acción registrada (§3.3). {@code details} es el JSON guardado, tal cual. */
    public record AuditEvent(UUID id, UUID adminId, AdminAction action, String targetType, UUID targetId,
                             @JsonRawValue String details, AdminAuditOutcome outcome, Instant occurredAt) {

        static AuditEvent from(AdminAuditEvent event) {
            return new AuditEvent(event.getId(), event.getAdminId(), event.getAction(), event.getTargetType(),
                    event.getTargetId(), event.getDetails(), event.getOutcome(), event.getOccurredAt());
        }
    }

    public record AuditPage(List<AuditEvent> items, String nextCursor) {
    }
}
