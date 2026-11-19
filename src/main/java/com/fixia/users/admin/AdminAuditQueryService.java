package com.fixia.users.admin;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Consulta del registro de acciones del ADMIN en ms-users (contrato {@code docs/contracts/admin.md} §3.3). */
@Service
public class AdminAuditQueryService {

    private final AdminAuditEventRepository repository;
    private final AdminAuditService audit;

    public AdminAuditQueryService(AdminAuditEventRepository repository, AdminAuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdminResponses.AuditPage list(UUID adminId, UUID adminFilter, String actionParam, Integer limitParam,
                                         String cursorParam) {
        AdminAction action = AdminQueries.parseEnum(AdminAction.class, actionParam, "action");
        int limit = AdminQueries.limit(limitParam);
        AdminCursor cursor = AdminCursor.decode(cursorParam);

        Specification<AdminAuditEvent> spec =
                Specification.where(AdminQueries.<AdminAuditEvent>after(cursor, "occurredAt"));
        if (adminFilter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("adminId"), adminFilter));
        }
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }

        // Se lee antes de registrar esta consulta, para que la página no se incluya a sí misma.
        List<AdminAuditEvent> rows = new ArrayList<>(
                repository.findAll(spec, AdminQueries.firstRows(limit, "occurredAt")).getContent());
        String next = AdminQueries.nextCursor(rows, limit,
                event -> new AdminCursor(event.getOccurredAt(), event.getId()));

        Map<String, Object> filters = new LinkedHashMap<>();
        if (adminFilter != null) {
            filters.put("adminId", adminFilter);
        }
        if (action != null) {
            filters.put("action", action);
        }
        filters.put("limit", limit);
        audit.record(adminId, AdminAction.AUDIT_LIST, null, filters, AdminAuditOutcome.OK);

        return new AdminResponses.AuditPage(rows.stream().map(AdminResponses.AuditEvent::from).toList(), next);
    }
}
