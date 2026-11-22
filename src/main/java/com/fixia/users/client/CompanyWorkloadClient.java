package com.fixia.users.client;

import com.fixia.users.config.InternalApiKeyFilter;
import com.fixia.users.config.InternalApiProperties;
import com.fixia.users.exception.ServicesUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Lo que necesita ms-users antes de dejar que un técnico se desvincule de su empresa (GC-524, contrato de empresas §6):
 * su trabajo pendiente, que sabe ms-services, y el dinero retenido, que sabe ms-payments. Ninguna base ajena se lee
 * (Database per Service). Si alguno no responde bien, se lanza {@link ServicesUnavailableException} y no se desvincula:
 * falla cerrado, igual que la verificación de las evaluaciones.
 */
@Component
public class CompanyWorkloadClient {

    /** Máximo de solicitudes que ms-payments acepta por consulta. */
    static final int HELD_BATCH = 500;

    /** Respuesta de ms-services (§6.1). */
    public record Workload(List<UUID> pendingServiceRequestIds, List<UUID> serviceRequestIds) {
    }

    record WorkloadRequest(UUID companyAdminUserId, UUID technicianUserId) {
    }

    record HeldRequest(UUID providerId, List<UUID> serviceRequestIds) {
    }

    record HeldResponse(List<UUID> heldServiceRequestIds) {
    }

    private final RestClient servicesClient;
    private final RestClient paymentsClient;
    private final InternalApiProperties internalApiProperties;

    public CompanyWorkloadClient(@Qualifier("servicesRestClient") RestClient servicesClient,
                                 @Qualifier("paymentsRestClient") RestClient paymentsClient,
                                 InternalApiProperties internalApiProperties) {
        this.servicesClient = servicesClient;
        this.paymentsClient = paymentsClient;
        this.internalApiProperties = internalApiProperties;
    }

    /** Trabajo del técnico con la empresa, según ms-services. */
    public Workload workload(UUID companyAdminUserId, UUID technicianUserId) {
        Workload workload;
        try {
            workload = servicesClient.post()
                    .uri("/internal/services/company-executors/workload")
                    .header(InternalApiKeyFilter.HEADER, apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new WorkloadRequest(companyAdminUserId, technicianUserId))
                    .retrieve()
                    .body(Workload.class);
        } catch (RestClientException e) {
            throw new ServicesUnavailableException("ms-services no pudo verificar el trabajo del técnico", e);
        }
        if (workload == null || workload.pendingServiceRequestIds() == null || workload.serviceRequestIds() == null) {
            throw new ServicesUnavailableException("ms-services respondió un trabajo incompleto", null);
        }
        return workload;
    }

    /**
     * De esas solicitudes, las que tienen dinero de la empresa retenido o por retener, según ms-payments. Se pregunta en
     * lotes de {@value #HELD_BATCH}; sin solicitudes no hay nada que preguntar.
     */
    public List<UUID> held(UUID companyAdminUserId, List<UUID> serviceRequestIds) {
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(serviceRequestIds));
        List<UUID> held = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += HELD_BATCH) {
            List<UUID> batch = ids.subList(from, Math.min(ids.size(), from + HELD_BATCH));
            HeldResponse response;
            try {
                response = paymentsClient.post()
                        .uri("/internal/payments/held")
                        .header(InternalApiKeyFilter.HEADER, apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new HeldRequest(companyAdminUserId, batch))
                        .retrieve()
                        .body(HeldResponse.class);
            } catch (RestClientException e) {
                throw new ServicesUnavailableException("ms-payments no pudo verificar el dinero retenido", e);
            }
            if (response == null || response.heldServiceRequestIds() == null) {
                throw new ServicesUnavailableException("ms-payments respondió una consulta incompleta", null);
            }
            held.addAll(response.heldServiceRequestIds());
        }
        return held;
    }

    private String apiKey() {
        return internalApiProperties.apiKey() == null ? "" : internalApiProperties.apiKey();
    }
}
