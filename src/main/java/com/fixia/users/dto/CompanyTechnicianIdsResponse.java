package com.fixia.users.dto;

import java.util.List;
import java.util.UUID;

/**
 * Técnicos activos de la empresa ({@code users.id}), en el orden en que se asociaron. Lista vacía si la cuenta no es de
 * una empresa activa o si la empresa no tiene técnicos: quien consulta no aprende cuál de los dos casos es.
 */
public record CompanyTechnicianIdsResponse(List<UUID> technicianUserIds) {
}
