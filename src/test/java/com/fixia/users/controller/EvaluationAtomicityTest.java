package com.fixia.users.controller;

import com.fixia.users.client.ServiceParticipationClient;
import com.fixia.users.client.ServiceParticipationClient.Participation;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.repository.OutboxEventRepository;
import com.fixia.users.repository.ServiceEvaluationRepository;
import com.fixia.users.repository.UserRepository;
import com.fixia.users.service.EvaluationEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * La evaluación y su evento se guardan juntos o no se guarda ninguno (GC-444, Outbox transaccional): si el evento no
 * se puede escribir, la evaluación se revierte.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvaluationAtomicityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceEvaluationRepository evaluationRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @MockBean
    private ServiceParticipationClient participationClient;

    @MockBean
    private EvaluationEvents events;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private UUID account(String email, String document, Role role) {
        return userRepository.save(new User(email, "hash", role, "Laura", "Gómez", DocumentType.CC, document,
                "3104445566", "v1.0", Instant.now())).getId();
    }

    @Test
    void siElEventoNoSePuedeEscribirLaEvaluacionSeRevierte() {
        UUID client = account("cliente@example.com", "100", Role.CLIENT);
        UUID technician = account("tecnico@example.com", "200", Role.PROFESSIONAL);
        UUID service = UUID.randomUUID();
        doReturn(Optional.of(new Participation(service, "CLOSED", client, technician, "INDEPENDENT",
                Instant.now()))).when(participationClient).find(service);
        doThrow(new IllegalStateException("No se pudo serializar el evento")).when(events).registered(any(), any());

        assertThatThrownBy(() -> mockMvc.perform(post("/api/users/evaluations")
                .with(jwt().jwt(token -> token.subject("usr_" + client))
                        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceRequestId\":\"" + service + "\",\"rating\":5}")))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(evaluationRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }
}
