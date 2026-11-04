package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.AvailabilitySlot;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API de zona de atención y disponibilidad del técnico autenticado (RF-011). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TechnicianAvailabilityControllerTest {

    private static final String ZONE = "/api/users/technicians/me/coverage-zone";
    private static final String AVAILABILITY = "/api/users/technicians/me/availability";
    private static final String PROFILE = "/api/users/technicians/me/profile";
    private static final String PASSWORD = "Tecnico123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // DELETE inmediato en la base; el borrado en cascada se lleva técnicos, categorías y disponibilidad.
        userRepository.deleteAllInBatch();
    }

    // ---------- Utilidades ----------

    private Map<String, Object> account(String email, String documentNumber) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Carlos");
        body.put("lastName", "Gómez");
        body.put("documentType", "CC");
        body.put("documentNumber", documentNumber);
        body.put("email", email);
        body.put("phone", "+573104445566");
        body.put("password", PASSWORD);
        body.put("policyVersion", "v1.0");
        body.put("consentAccepted", true);
        return body;
    }

    private UUID register(String path, String email, String documentNumber) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(account(email, documentNumber))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID registerTechnician(String email, String documentNumber) throws Exception {
        return register("/api/users/technicians", email, documentNumber);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private ResultActions putJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(put(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions getWithToken(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static Map<String, Object> zone(String city, Object latitude, Object longitude, Object radiusKm) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("city", city);
        body.put("latitude", latitude);
        body.put("longitude", longitude);
        body.put("radiusKm", radiusKm);
        return body;
    }

    private static Map<String, Object> slot(String day, String start, String end) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dayOfWeek", day);
        body.put("startTime", start);
        body.put("endTime", end);
        return body;
    }

    private static Map<String, Object> slots(Object... slots) {
        return Map.of("slots", List.of(slots));
    }

    private int storedAvailabilityRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM technician_availability", Integer.class);
    }

    /** Lanza todas las peticiones a la vez y devuelve el código HTTP de cada una. */
    private List<Integer> runAtOnce(List<Callable<ResultActions>> requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> pending = new ArrayList<>();
            for (Callable<ResultActions> request : requests) {
                pending.add(pool.submit(() -> {
                    start.await();
                    return request.call().andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : pending) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- Zona de atención ----------

    @Test
    void unTecnicoNuevoNoTieneZonaDeAtencion() throws Exception {
        registerTechnician("carlos@example.com", "79800900");

        getWithToken(ZONE, login("carlos@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").isEmpty())
                .andExpect(jsonPath("$.latitude").isEmpty())
                .andExpect(jsonPath("$.radiusKm").isEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void declaraUnaZonaDeSoloCiudadYLaPuedePrecisarConUnCirculo() throws Exception {
        UUID userId = registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");

        putJson(ZONE, token, zone("  Bogotá ", null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Bogotá"))
                .andExpect(jsonPath("$.radiusKm").isEmpty());

        putJson(ZONE, token, zone("Bogotá", 4.653281, -74.083649, 15))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(4.65))
                .andExpect(jsonPath("$.longitude").value(-74.08))
                .andExpect(jsonPath("$.radiusKm").value(15));

        getWithToken(ZONE, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Bogotá"))
                .andExpect(jsonPath("$.radiusKm").value(15));
        assertThat(technicianRepository.findByUserId(userId).orElseThrow().getCoverageZone().hasCircle()).isTrue();
    }

    @Test
    void rechazaUnaZonaInvalidaYNoCambiaLoGuardado() throws Exception {
        UUID userId = registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");
        putJson(ZONE, token, zone("Cali", null, null, null)).andExpect(status().isOk());

        putJson(ZONE, token, zone(" ", null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("city")));
        putJson(ZONE, token, zone("Cali", 91, -76.5, 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("latitude")));
        putJson(ZONE, token, zone("Cali", 3.45, -76.53, 101))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("radiusKm")));
        putJson(ZONE, token, zone("Cali", 3.45, null, 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.detail").value("El círculo de la zona necesita latitud, longitud y radio"));

        assertThat(technicianRepository.findByUserId(userId).orElseThrow().getCoverageZone().getCity())
                .isEqualTo("Cali");
    }

    @Test
    void elTecnicoPuedeRetirarSuZona() throws Exception {
        UUID userId = registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");
        putJson(ZONE, token, zone("Bogotá", 4.65, -74.08, 15)).andExpect(status().isOk());

        mockMvc.perform(delete(ZONE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        getWithToken(ZONE, token).andExpect(status().isOk()).andExpect(jsonPath("$.city").isEmpty());
        assertThat(technicianRepository.findByUserId(userId).orElseThrow().getCoverageZone()).isNull();
    }

    // ---------- Disponibilidad ----------

    @Test
    void unTecnicoNuevoNoTieneDisponibilidad() throws Exception {
        registerTechnician("carlos@example.com", "79800900");

        getWithToken(AVAILABILITY, login("carlos@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots").isEmpty())
                .andExpect(jsonPath("$.timeZone").value("America/Bogota"));
    }

    @Test
    void declaraSuDisponibilidadYLaRecibeOrdenada() throws Exception {
        UUID userId = registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");

        putJson(AVAILABILITY, token, slots(
                slot("SATURDAY", "09:00", "13:00"),
                slot("MONDAY", "14:00", "18:00"),
                slot("MONDAY", "08:00", "12:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(3))
                .andExpect(jsonPath("$.slots[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.slots[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.slots[0].endTime").value("12:00"))
                .andExpect(jsonPath("$.slots[2].dayOfWeek").value("SATURDAY"));

        getWithToken(AVAILABILITY, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(3))
                .andExpect(jsonPath("$.slots[1].startTime").value("14:00"));
        // 2026-10-19 es lunes: 14:00 UTC son las 09:00 en Bogotá.
        assertThat(technicianRepository.findByUserId(userId).orElseThrow()
                .isAvailableAt(Instant.parse("2026-10-19T14:00:00Z"))).isTrue();
    }

    @Test
    void reemplazaYVaciaLaDisponibilidad() throws Exception {
        registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");
        putJson(AVAILABILITY, token, slots(slot("MONDAY", "08:00", "12:00"), slot("TUESDAY", "08:00", "12:00")))
                .andExpect(status().isOk());

        putJson(AVAILABILITY, token, slots(slot("MONDAY", "08:00", "13:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(1))
                .andExpect(jsonPath("$.slots[0].endTime").value("13:00"));
        assertThat(storedAvailabilityRows()).isEqualTo(1);

        putJson(AVAILABILITY, token, Map.of("slots", List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots").isEmpty());
        assertThat(storedAvailabilityRows()).isZero();
    }

    @Test
    void rechazaFranjasInvalidasYNoCambiaLoGuardado() throws Exception {
        registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");
        putJson(AVAILABILITY, token, slots(slot("MONDAY", "08:00", "12:00"))).andExpect(status().isOk());

        putJson(AVAILABILITY, token, slots(slot("MONDAY", "08:00", "12:00"), slot("MONDAY", "11:00", "15:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Las franjas de disponibilidad no pueden cruzarse"));
        putJson(AVAILABILITY, token, slots(slot("TUESDAY", "12:00", "08:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La hora de inicio debe ser anterior a la hora de fin"));
        putJson(AVAILABILITY, token, slots(slot("LUNES", "08:00", "12:00"))).andExpect(status().isBadRequest());
        putJson(AVAILABILITY, token, slots(slot("MONDAY", "8 am", "12:00"))).andExpect(status().isBadRequest());
        putJson(AVAILABILITY, token, slots(slot("MONDAY", null, "12:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("slots[0].startTime")));
        putJson(AVAILABILITY, token, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("slots")));

        getWithToken(AVAILABILITY, token)
                .andExpect(jsonPath("$.slots.length()").value(1))
                .andExpect(jsonPath("$.slots[0].endTime").value("12:00"));
    }

    @Test
    void laZonaYLaDisponibilidadNoTocanElRestoDelPerfil() throws Exception {
        registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("professionalDescription", "Plomero residencial");
        profile.put("yearsOfExperience", 8);
        profile.put("categories", List.of("PLUMBING"));
        putJson(PROFILE, token, profile).andExpect(status().isOk());

        putJson(ZONE, token, zone("Bogotá", null, null, null)).andExpect(status().isOk());
        putJson(AVAILABILITY, token, slots(slot("MONDAY", "08:00", "12:00"))).andExpect(status().isOk());

        getWithToken(PROFILE, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalDescription").value("Plomero residencial"))
                .andExpect(jsonPath("$.categories[0]").value("PLUMBING"));
    }

    // ---------- Autorización ----------

    @Test
    void cadaTecnicoSoloVeYModificaLoSuyo() throws Exception {
        UUID carlos = registerTechnician("carlos@example.com", "79800900");
        registerTechnician("laura@example.com", "52600700");
        String laura = login("laura@example.com");

        putJson(ZONE, laura, zone("Medellín", null, null, null)).andExpect(status().isOk());
        putJson(AVAILABILITY, laura, slots(slot("FRIDAY", "08:00", "12:00"))).andExpect(status().isOk());

        String carlosToken = login("carlos@example.com");
        getWithToken(ZONE, carlosToken).andExpect(jsonPath("$.city").isEmpty());
        getWithToken(AVAILABILITY, carlosToken).andExpect(jsonPath("$.slots").isEmpty());
        Technician stored = technicianRepository.findByUserId(carlos).orElseThrow();
        assertThat(stored.getCoverageZone()).isNull();
        assertThat(stored.getAvailability()).isEmpty();
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(ZONE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(AVAILABILITY)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ZONE)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(AVAILABILITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slots(slot("MONDAY", "08:00", "12:00")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unClienteNoPuedeGestionarZonaNiDisponibilidad() throws Exception {
        register("/api/users/clients", "cliente@example.com", "1020304050");
        String token = login("cliente@example.com");

        getWithToken(ZONE, token).andExpect(status().isForbidden());
        putJson(ZONE, token, zone("Bogotá", null, null, null)).andExpect(status().isForbidden());
        getWithToken(AVAILABILITY, token).andExpect(status().isForbidden());
        putJson(AVAILABILITY, token, slots(slot("MONDAY", "08:00", "12:00"))).andExpect(status().isForbidden());
        assertThat(storedAvailabilityRows()).isZero();
    }

    @Test
    void unaCuentaProfessionalSinPerfilDeTecnicoResponde404() throws Exception {
        userRepository.save(new User("sinperfil@example.com", passwordEncoder.encode(PASSWORD), Role.PROFESSIONAL,
                "Sin", "Perfil", DocumentType.CC, "33333333", "3001112233", "v1.0", Instant.now()));
        String token = login("sinperfil@example.com");

        getWithToken(ZONE, token).andExpect(status().isNotFound());
        putJson(ZONE, token, zone("Bogotá", null, null, null)).andExpect(status().isNotFound());
        mockMvc.perform(delete(ZONE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        putJson(AVAILABILITY, token, slots(slot("MONDAY", "08:00", "12:00"))).andExpect(status().isNotFound());
    }

    // ---------- Concurrencia ----------

    @Test
    void variasActualizacionesSimultaneasDeDisponibilidadSeAplicanSinConflicto() throws Exception {
        UUID userId = registerTechnician("carlos@example.com", "79800900");
        String token = login("carlos@example.com");
        List<Callable<ResultActions>> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Map<String, Object> body = i % 2 == 0
                    ? slots(slot("MONDAY", "08:00", "12:00"), slot("TUESDAY", "08:00", "12:00"))
                    : slots(slot("MONDAY", "08:00", "12:00"), slot("WEDNESDAY", "14:00", "18:00"));
            requests.add(() -> putJson(AVAILABILITY, token, body));
        }

        assertThat(runAtOnce(requests)).containsOnly(200);

        assertThat(technicianRepository.findByUserId(userId).orElseThrow().getAvailability())
                .hasSize(2)
                .contains(new AvailabilitySlot(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)));
        assertThat(storedAvailabilityRows()).isEqualTo(2);
    }
}
