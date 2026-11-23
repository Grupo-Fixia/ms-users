package com.fixia.users.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.exception.ServicesUnavailableException;
import com.fixia.users.replication.ReplicatedStates;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Quién participó en un servicio y si está cerrado (GC-444, contrato de GC-443). Desde GC-547 ya no se pregunta a
 * ms-services: se lee la réplica local del estado {@code servicio.participacion} que ms-services publica en
 * {@code fixia.services.participations}. La fuente de verdad sigue siendo ms-services; la copia puede ir unos segundos
 * atrasada (un servicio recién cerrado puede verse aún como no cerrado y responder 422 durante ese lapso).
 */
@Component
public class ServiceParticipationClient {

    public static final String STATE_TYPE = "servicio.participacion";

    /** Lo que publica ms-services. Estado y tipo de prestador como texto: este servicio no copia sus enumeraciones. */
    public record Participation(UUID serviceRequestId, String status, UUID clientId, UUID providerId,
                                String providerType, Instant closedAt, UUID executorId) {

        public Participation(UUID serviceRequestId, String status, UUID clientId, UUID providerId,
                             String providerType, Instant closedAt) {
            this(serviceRequestId, status, clientId, providerId, providerType, closedAt, null);
        }

        /**
         * Quién ejecutó el servicio (GC-551): a él se le evalúa y él evalúa al cliente. En un independiente es el
         * prestador; en una empresa, su técnico designado. Si ms-services aún no lo envía, es el prestador.
         */
        public UUID executor() {
            return executorId != null ? executorId : providerId;
        }

        public boolean isClosed() {
            return "CLOSED".equals(status);
        }

        public boolean isCompanyProvider() {
            return "COMPANY".equals(providerType);
        }
    }

    private final ReplicatedStates states;
    private final ObjectMapper objectMapper;

    public ServiceParticipationClient(ReplicatedStates states, ObjectMapper objectMapper) {
        this.states = states;
        this.objectMapper = objectMapper;
    }

    /**
     * @return la participación, o vacío si la réplica no conoce el servicio
     * @throws ServicesUnavailableException si la copia local está incompleta: sin ella no se evalúa (falla cerrado)
     */
    public Optional<Participation> find(UUID serviceRequestId) {
        Optional<JsonNode> state = states.find(STATE_TYPE, serviceRequestId);
        if (state.isEmpty()) {
            return Optional.empty();
        }
        Participation participation;
        try {
            participation = objectMapper.treeToValue(state.get(), Participation.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ServicesUnavailableException("La participación replicada no se pudo leer", e);
        }
        if (participation == null || !serviceRequestId.equals(participation.serviceRequestId())
                || participation.status() == null || participation.clientId() == null
                || (participation.isClosed() && (participation.providerId() == null
                || participation.closedAt() == null))) {
            throw new ServicesUnavailableException("La participación replicada está incompleta", null);
        }
        return Optional.of(participation);
    }
}
