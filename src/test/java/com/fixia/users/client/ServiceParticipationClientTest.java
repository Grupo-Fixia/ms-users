package com.fixia.users.client;

import com.fixia.users.client.ServiceParticipationClient.Participation;
import com.fixia.users.config.InternalApiProperties;
import com.fixia.users.exception.ServicesUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ServiceParticipationClientTest {

    private static final String BASE_URL = "http://ms-services:8080";
    private static final UUID SERVICE = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID PROVIDER = UUID.randomUUID();
    private static final String URL = BASE_URL + "/internal/services/" + SERVICE + "/participation";

    private MockRestServiceServer server;
    private ServiceParticipationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ServiceParticipationClient(builder.build(), new InternalApiProperties("secreto-interno"));
    }

    private static String body(Object id, String status, Object provider, String providerType, String closedAt) {
        return "{\"serviceRequestId\":" + quote(id) + ",\"status\":" + quote(status) + ",\"clientId\":\"" + CLIENT
                + "\",\"providerId\":" + quote(provider) + ",\"providerType\":" + quote(providerType)
                + ",\"closedAt\":" + quote(closedAt) + "}";
    }

    private static String quote(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test
    void consultaLaParticipacionConLaClaveCompartida() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Api-Key", "secreto-interno"))
                .andRespond(withSuccess(body(SERVICE, "CLOSED", PROVIDER, "INDEPENDENT", "2026-10-29T10:00:00Z"),
                        MediaType.APPLICATION_JSON));

        Participation participation = client.find(SERVICE).orElseThrow();

        assertThat(participation.isClosed()).isTrue();
        assertThat(participation.isCompanyProvider()).isFalse();
        assertThat(participation.clientId()).isEqualTo(CLIENT);
        assertThat(participation.providerId()).isEqualTo(PROVIDER);
        assertThat(participation.closedAt()).isEqualTo(Instant.parse("2026-10-29T10:00:00Z"));
        server.verify();
    }

    @Test
    void unServicioAbiertoSinPrestadorTambienEsUnaRespuestaValida() {
        server.expect(requestTo(URL)).andRespond(withSuccess(body(SERVICE, "PUBLISHED", null, null, null),
                MediaType.APPLICATION_JSON));

        Participation participation = client.find(SERVICE).orElseThrow();

        assertThat(participation.isClosed()).isFalse();
        assertThat(participation.providerId()).isNull();
    }

    @Test
    void unServicioQueMsServicesNoConoceEsVacio() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.find(SERVICE)).isEmpty();
    }

    @Test
    void sinClaveConfiguradaEnviaLaCabeceraVaciaYMsServicesLaRechaza() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer unauthorized = MockRestServiceServer.bindTo(builder).build();
        ServiceParticipationClient withoutKey = new ServiceParticipationClient(builder.build(),
                new InternalApiProperties(null));
        unauthorized.expect(requestTo(URL)).andExpect(header("X-Internal-Api-Key", ""))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> withoutKey.find(SERVICE)).isInstanceOf(ServicesUnavailableException.class);
    }

    @Test
    void sinRespuestaValidaNoSeDaPorVerificado() {
        for (HttpStatus failure : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.INTERNAL_SERVER_ERROR,
                HttpStatus.SERVICE_UNAVAILABLE)) {
            server.reset();
            server.expect(requestTo(URL)).andRespond(withStatus(failure));
            assertThatThrownBy(() -> client.find(SERVICE)).as(failure.toString())
                    .isInstanceOf(ServicesUnavailableException.class);
        }
        server.reset();
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("timeout")));
        assertThatThrownBy(() -> client.find(SERVICE)).isInstanceOf(ServicesUnavailableException.class);

        List<String> invalid = List.of(
                "",
                body(UUID.randomUUID(), "CLOSED", PROVIDER, "INDEPENDENT", "2026-10-29T10:00:00Z"),
                body(null, "CLOSED", PROVIDER, "INDEPENDENT", "2026-10-29T10:00:00Z"),
                body(SERVICE, null, PROVIDER, "INDEPENDENT", "2026-10-29T10:00:00Z"),
                body(SERVICE, "CLOSED", PROVIDER, "INDEPENDENT", null),
                body(SERVICE, "CLOSED", null, null, "2026-10-29T10:00:00Z"),
                "{\"serviceRequestId\":\"" + SERVICE + "\",\"status\":\"CLOSED\"}");
        for (String answer : invalid) {
            server.reset();
            server.expect(requestTo(URL)).andRespond(withSuccess(answer, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> client.find(SERVICE)).as(answer)
                    .isInstanceOf(ServicesUnavailableException.class);
        }
    }
}
