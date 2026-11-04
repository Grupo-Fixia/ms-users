package com.fixia.users.client;

import com.fixia.users.config.InternalApiKeyFilter;
import com.fixia.users.config.InternalApiProperties;
import com.fixia.users.exception.ServicesUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Pregunta a ms-services quién participó en un servicio y si está cerrado (GC-444, contrato de GC-443). ms-users no lee
 * la base de ms-services (Database per Service): usa la ruta interna con la clave compartida.
 */
@Component
public class ServiceParticipationClient {

    /** Lo que responde ms-services. Estado y tipo de prestador como texto: este servicio no copia sus enumeraciones. */
    public record Participation(UUID serviceRequestId, String status, UUID clientId, UUID providerId,
                                String providerType, Instant closedAt) {

        public boolean isClosed() {
            return "CLOSED".equals(status);
        }

        public boolean isCompanyProvider() {
            return "COMPANY".equals(providerType);
        }
    }

    private final RestClient restClient;
    private final InternalApiProperties internalApiProperties;

    public ServiceParticipationClient(@Qualifier("servicesRestClient") RestClient restClient,
                                      InternalApiProperties internalApiProperties) {
        this.restClient = restClient;
        this.internalApiProperties = internalApiProperties;
    }

    /**
     * @return la participación, o vacío si ms-services no conoce el servicio (404)
     * @throws ServicesUnavailableException si no se obtuvo una respuesta válida: sin ella no se evalúa (falla cerrado)
     */
    public Optional<Participation> find(UUID serviceRequestId) {
        Participation participation;
        try {
            participation = restClient.get()
                    .uri("/internal/services/{id}/participation", serviceRequestId)
                    .header(InternalApiKeyFilter.HEADER, apiKey())
                    .retrieve()
                    .body(Participation.class);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ServicesUnavailableException("ms-services no pudo verificar la participación", e);
        }
        if (participation == null || !serviceRequestId.equals(participation.serviceRequestId())
                || participation.status() == null || participation.clientId() == null
                || (participation.isClosed() && (participation.providerId() == null
                || participation.closedAt() == null))) {
            throw new ServicesUnavailableException("ms-services respondió una participación incompleta", null);
        }
        return Optional.of(participation);
    }

    private String apiKey() {
        return internalApiProperties.apiKey() == null ? "" : internalApiProperties.apiKey();
    }
}
