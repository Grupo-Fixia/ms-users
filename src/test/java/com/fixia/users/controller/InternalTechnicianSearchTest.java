package com.fixia.users.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.CoverageZone;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.ServiceCategory;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Búsqueda interna de técnicos por categoría y ciudad (GC-900), la que consume ms-matching-geo. Como en las demás
 * pruebas de rutas internas, la llamada del otro servicio se simula con una autenticación interna ya establecida.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalTechnicianSearchTest {

    private static final String SEARCH = "/internal/technicians/search";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private int sequence;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
        sequence = 0;
    }

    // ---------- Utilidades ----------

    /** Crea una cuenta y, si tiene rol PROFESSIONAL o se pide, su perfil de técnico con categorías y zona. */
    private UUID technician(Role role, boolean active, Set<ServiceCategory> categories, CoverageZone zone) {
        sequence++;
        User user = new User("tecnico" + sequence + "@example.com", "hash", role, "Carlos", "Gómez", DocumentType.CC,
                String.valueOf(79800000 + sequence), "3104445566", "v1.0", Instant.now());
        if (!active) {
            user.disable();
        }
        UUID userId = userRepository.save(user).getId();
        technicianRepository.save(new Technician(userId, Instant.now()));
        transactionTemplate.executeWithoutResult(status -> {
            Technician saved = technicianRepository.findByUserId(userId).orElseThrow();
            saved.replaceCategories(categories, Instant.now());
            saved.updateCoverageZone(zone, Instant.now());
        });
        return userId;
    }

    private UUID plumberIn(String city) {
        return technician(Role.PROFESSIONAL, true, Set.of(ServiceCategory.PLUMBING), new CoverageZone(city));
    }

    private static Map<String, Object> body(String category, String city, Integer limit, String cursor) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("category", category);
        data.put("city", city);
        data.put("limit", limit);
        data.put("cursor", cursor);
        return data;
    }

    private ResultActions search(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(SEARCH).with(InternalTechnicianControllerTest.internalService())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    // ---------- Reglas ----------

    @Test
    void encuentraPorCategoriaYCiudadSinImportarTildesNiMayusculas() throws Exception {
        UUID bogota = technician(Role.PROFESSIONAL, true, Set.of(ServiceCategory.PLUMBING, ServiceCategory.ELECTRICAL),
                new CoverageZone("Bogotá"));

        search(body("PLUMBING", " BOGOTA ", null, null))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(bogota.toString()))
                .andExpect(jsonPath("$.items[0].displayName").value("Carlos G."))
                .andExpect(jsonPath("$.items[0].city").value("Bogotá"))
                // Orden de declaración del enum, no alfabético: es estable entre llamadas.
                .andExpect(jsonPath("$.items[0].categories[0]").value("PLUMBING"))
                .andExpect(jsonPath("$.items[0].categories[1]").value("ELECTRICAL"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void devuelveElCirculoSoloSiElTecnicoLoDefinio() throws Exception {
        UUID withCircle = technician(Role.PROFESSIONAL, true, Set.of(ServiceCategory.PLUMBING),
                new CoverageZone("Bogotá", 4.65, -74.08, 10));
        UUID cityOnly = plumberIn("Bogotá");

        String json = search(body("PLUMBING", "Bogotá", null, null)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode items = objectMapper.readTree(json).get("items");
        for (JsonNode item : items) {
            if (item.get("userId").asText().equals(withCircle.toString())) {
                assertThat(item.get("circle").get("latitude").asDouble()).isEqualTo(4.65);
                assertThat(item.get("circle").get("longitude").asDouble()).isEqualTo(-74.08);
                assertThat(item.get("circle").get("radiusKm").asInt()).isEqualTo(10);
            } else {
                assertThat(item.get("userId").asText()).isEqualTo(cityOnly.toString());
                assertThat(item.get("circle").isNull()).isTrue();
            }
        }
        assertThat(items).hasSize(2);
    }

    @Test
    void excluyeLoQueNoCumpleCategoriaCiudadCuentaNiRol() throws Exception {
        UUID match = plumberIn("Bogotá");
        technician(Role.PROFESSIONAL, true, Set.of(ServiceCategory.ELECTRICAL), new CoverageZone("Bogotá"));
        plumberIn("Medellín");
        technician(Role.PROFESSIONAL, false, Set.of(ServiceCategory.PLUMBING), new CoverageZone("Bogotá"));
        technician(Role.PROFESSIONAL, true, Set.of(ServiceCategory.PLUMBING), null);
        technician(Role.CLIENT, true, Set.of(ServiceCategory.PLUMBING), new CoverageZone("Bogotá"));

        search(body("PLUMBING", "Bogotá", null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(match.toString()));
    }

    @Test
    void sinCoincidenciasResponde200ConListaVacia() throws Exception {
        plumberIn("Medellín");

        search(body("PLUMBING", "Bogotá", null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void recorrerLasPaginasEntregaCadaTecnicoUnaSolaVezEnElMismoOrden() throws Exception {
        List<UUID> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(plumberIn("Bogotá"));
        }

        List<String> all = userIds(search(body("PLUMBING", "Bogotá", 50, null)));
        assertThat(all).hasSize(5).containsExactlyInAnyOrderElementsOf(created.stream().map(UUID::toString).toList());

        List<String> paged = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            ResultActions page = search(body("PLUMBING", "Bogotá", 2, cursor));
            JsonNode json = objectMapper.readTree(page.andReturn().getResponse().getContentAsString());
            json.get("items").forEach(item -> paged.add(item.get("userId").asText()));
            cursor = json.get("nextCursor").isNull() ? null : json.get("nextCursor").asText();
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(paged).containsExactlyElementsOf(all);
        assertThat(pages).isEqualTo(3);
    }

    @Test
    void noExponeDatosPersonalesDelTecnico() throws Exception {
        plumberIn("Bogotá");

        search(body("PLUMBING", "Bogotá", null, null))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("@example.com"))))
                .andExpect(content().string(not(containsString("3104445566"))))
                .andExpect(content().string(not(containsString("7980"))))
                .andExpect(content().string(not(containsString("Gómez"))));
    }

    // ---------- Validación y seguridad ----------

    @Test
    void rechazaUnaConsultaIncompletaOFueraDeRango() throws Exception {
        search(body(null, "Bogotá", null, null)).andExpect(status().isBadRequest());
        search(body("PLUMBING", "  ", null, null)).andExpect(status().isBadRequest());
        search(body("PLUMBING", null, null, null)).andExpect(status().isBadRequest());
        search(body("PLUMBING", "x".repeat(101), null, null)).andExpect(status().isBadRequest());
        search(body("PLUMBING", "Bogotá", 0, null)).andExpect(status().isBadRequest());
        search(body("PLUMBING", "Bogotá", 51, null)).andExpect(status().isBadRequest());
        search(body("NO_EXISTE", "Bogotá", null, null)).andExpect(status().isBadRequest());
    }

    @Test
    void rechazaUnCursorQueEsteServicioNoEmitio() throws Exception {
        search(body("PLUMBING", "Bogotá", null, "no-es-un-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El cursor no es válido"));
    }

    @Test
    void sinLaClaveInternaResponde401() throws Exception {
        mockMvc.perform(post(SEARCH).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("PLUMBING", "Bogotá", null, null))))
                .andExpect(status().isUnauthorized());
    }

    private List<String> userIds(ResultActions result) throws Exception {
        JsonNode json = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        json.get("items").forEach(item -> ids.add(item.get("userId").asText()));
        return ids;
    }
}
