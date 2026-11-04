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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Con {@code fixia.eligibility.require-verified} activo, la búsqueda de técnicos (GC-474) solo devuelve a los de
 * verificación vigente (RN-001), el mismo criterio que la elegibilidad. Sin el flag, que es lo normal, aparecen todos:
 * eso lo cubre {@link InternalTechnicianSearchTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "fixia.eligibility.require-verified=true")
class InternalTechnicianSearchVerifiedTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int sequence;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
        sequence = 0;
    }

    /** Plomero en Bogotá; los técnicos nacen en PENDING, así que {@code verified} lo pasa a VALID. */
    private UUID plumber(boolean verified) {
        sequence++;
        UUID userId = userRepository.save(new User("verificado" + sequence + "@example.com", "hash", Role.PROFESSIONAL,
                "Carlos", "Gómez", DocumentType.CC, String.valueOf(79900000 + sequence), "3104445566", "v1.0",
                Instant.now())).getId();
        technicianRepository.save(new Technician(userId, Instant.now()));
        transactionTemplate.executeWithoutResult(status -> {
            Technician technician = technicianRepository.findByUserId(userId).orElseThrow();
            technician.replaceCategories(Set.of(ServiceCategory.PLUMBING), Instant.now());
            technician.updateCoverageZone(new CoverageZone("Bogotá"), Instant.now());
        });
        if (verified) {
            jdbcTemplate.update("UPDATE technicians SET verification_status = 'VALID' WHERE user_id = ?", userId);
        }
        return userId;
    }

    private List<String> search(int limit, String cursor, List<String> collectNext) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("category", "PLUMBING");
        body.put("city", "Bogotá");
        body.put("limit", limit);
        body.put("cursor", cursor);
        String json = mockMvc.perform(post("/internal/technicians/search")
                        .with(InternalTechnicianControllerTest.internalService())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        List<String> ids = new ArrayList<>();
        node.get("items").forEach(item -> ids.add(item.get("userId").asText()));
        collectNext.add(node.get("nextCursor").isNull() ? null : node.get("nextCursor").asText());
        return ids;
    }

    @Test
    void conVerificacionExigidaSoloAparecenLosTecnicosConVerificacionVigente() throws Exception {
        UUID valid = plumber(true);
        plumber(false);
        plumber(false);

        List<String> ids = search(50, null, new ArrayList<>());

        assertThat(ids).containsExactly(valid.toString());
    }

    @Test
    void sinNingunTecnicoVerificadoLaListaQuedaVaciaYNoFallaLaPaginacion() throws Exception {
        plumber(false);

        List<String> next = new ArrayList<>();
        assertThat(search(20, null, next)).isEmpty();
        assertThat(next).containsExactly((String) null);
    }

    @Test
    void laPaginacionConLaVerificacionExigidaNoOmiteNiDuplica() throws Exception {
        List<String> verified = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            verified.add(plumber(true).toString());
            plumber(false);
        }

        List<String> paged = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            List<String> next = new ArrayList<>();
            paged.addAll(search(2, cursor, next));
            cursor = next.get(0);
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(paged).containsExactlyInAnyOrderElementsOf(verified);
    }
}
