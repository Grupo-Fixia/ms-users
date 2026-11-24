package com.fixia.users.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fixia.users.replication.ReplicatedStates;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trabajo y dinero retenido de un técnico con su empresa, para decidir si puede desvincularse (GC-524, contrato de
 * empresas §6). Desde GC-547 no se pregunta a ms-services ni a ms-payments: se leen las réplicas locales de
 * {@code ejecutor.carga} (ms-services, {@code fixia.services.executor-workloads}) y {@code pago.retencion}
 * (ms-payments, {@code fixia.payments.held-services}).
 *
 * <p>Las copias pueden ir unos segundos atrasadas. Si en ese lapso la empresa logra contratar al técnico, ms-services lo
 * detecta al recibir la desvinculación (compensación de GC-547) y el caso queda para el administrador.
 */
@Component
public class CompanyWorkloadClient {

    public static final String WORKLOAD_TYPE = "ejecutor.carga";
    public static final String HELD_TYPE = "pago.retencion";

    public record Workload(List<UUID> pendingServiceRequestIds, List<UUID> serviceRequestIds) {
    }

    private final ReplicatedStates states;

    public CompanyWorkloadClient(ReplicatedStates states) {
        this.states = states;
    }

    /** Postulaciones pendientes y servicios en que fue el seleccionado, de ese técnico con esa empresa. */
    public Workload workload(UUID companyAdminUserId, UUID technicianUserId) {
        List<UUID> pending = new ArrayList<>();
        List<UUID> all = new ArrayList<>();
        states.find(WORKLOAD_TYPE, technicianUserId).ifPresent(state -> {
            for (JsonNode company : state.path("companies")) {
                if (companyAdminUserId.toString().equals(company.path("companyAdminUserId").asText())) {
                    company.path("pendingServiceRequestIds").forEach(id -> pending.add(UUID.fromString(id.asText())));
                    company.path("serviceRequestIds").forEach(id -> all.add(UUID.fromString(id.asText())));
                }
            }
        });
        return new Workload(List.copyOf(pending), List.copyOf(all));
    }

    /** De esos servicios, en cuáles la empresa tiene dinero retenido. */
    public List<UUID> held(UUID companyAdminUserId, List<UUID> serviceRequestIds) {
        Map<UUID, JsonNode> found = states.findAll(HELD_TYPE, new LinkedHashSet<>(serviceRequestIds));
        List<UUID> held = new ArrayList<>();
        found.forEach((serviceRequestId, state) -> {
            if (state.path("held").asBoolean(false)
                    && companyAdminUserId.toString().equals(state.path("providerId").asText())) {
                held.add(serviceRequestId);
            }
        });
        return held;
    }
}
