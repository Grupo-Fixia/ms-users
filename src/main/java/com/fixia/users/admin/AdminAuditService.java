package com.fixia.users.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Registra las acciones del ADMIN (RF-081, RF-079). Cada registro va en su propia transacción: en ms-users el ADMIN solo
 * consulta, así que el registro debe quedar aunque la consulta termine en 404 (que revierte la transacción de la consulta).
 */
@Service
public class AdminAuditService {

    private final AdminAuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminAuditService(AdminAuditEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID adminId, AdminAction action, UUID targetUserId, Map<String, ?> details,
                       AdminAuditOutcome outcome) {
        repository.save(new AdminAuditEvent(adminId, action, targetUserId, toJson(details), outcome, clock.instant()));
    }

    private String toJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudieron serializar los detalles de la acción", e);
        }
    }
}
