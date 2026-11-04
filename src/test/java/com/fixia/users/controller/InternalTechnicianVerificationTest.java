package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.AvailabilitySlot;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Con {@code fixia.eligibility.require-verified} activo, un técnico sin verificación vigente no es elegible (RN-001). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "fixia.eligibility.require-verified=true")
class InternalTechnicianVerificationTest {

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

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
    }

    private UUID completePlumber() {
        UUID userId = userRepository.save(new User("carlos@example.com", "hash", Role.PROFESSIONAL, "Carlos", "Gómez",
                DocumentType.CC, "79800900", "3104445566", "v1.0", Instant.now())).getId();
        technicianRepository.save(new Technician(userId, Instant.now()));
        transactionTemplate.executeWithoutResult(status -> {
            Technician technician = technicianRepository.findByUserId(userId).orElseThrow();
            technician.replaceCategories(Set.of(ServiceCategory.PLUMBING), Instant.now());
            technician.updateCoverageZone(new CoverageZone("Bogotá"), Instant.now());
            technician.replaceAvailability(Set.of(new AvailabilitySlot(DayOfWeek.MONDAY, LocalTime.of(8, 0),
                    LocalTime.of(12, 0))), Instant.now());
        });
        return userId;
    }

    private ResultActions evaluate(UUID userId) throws Exception {
        Map<String, Object> request = InternalTechnicianControllerTest.request(UUID.randomUUID(), "PLUMBING",
                "Bogotá", null, null, InternalTechnicianControllerTest.MONDAY_9AM);
        return mockMvc.perform(post("/internal/technicians/" + userId + "/eligibility")
                .with(InternalTechnicianControllerTest.internalService())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("requests", List.of(request)))));
    }

    @Test
    void sinVerificacionVigenteNoEsElegibleYConEllaSi() throws Exception {
        UUID technician = completePlumber();

        evaluate(technician)
                .andExpect(jsonPath("$.results[0].eligible").value(false))
                .andExpect(jsonPath("$.results[0].reasons", contains("VERIFICATION")));

        jdbcTemplate.update("UPDATE technicians SET verification_status = 'VALID' WHERE user_id = ?", technician);

        evaluate(technician).andExpect(jsonPath("$.results[0].eligible").value(true));
    }
}
