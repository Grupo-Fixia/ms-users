package com.fixia.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.repository.CompanyRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil público de las empresas para ms-services (GC-468): solo el nombre de las empresas activas, en el orden pedido.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalCompanyPublicProfilesTest {

    private static final String PATH = "/internal/companies/public-profiles";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
    }

    // ---------- Utilidades ----------

    private User account(String email, String document, Role role) {
        return userRepository.save(new User(email, "hash", role, "Laura", "Gómez", DocumentType.CC, document,
                "3104445566", "v1.0", Instant.now()));
    }

    private UUID company(String email, String document, String name) {
        User admin = account(email, document, Role.COMPANY);
        companyRepository.save(new Company(admin.getId(), name, "900" + document, Instant.now()));
        return admin.getId();
    }

    private ResultActions find(List<?> ids) throws Exception {
        return mockMvc.perform(post(PATH).with(InternalTechnicianControllerTest.internalService())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("companyAdminUserIds", ids))));
    }

    // ---------- Contenido ----------

    @Test
    void devuelveElNombreDeLasEmpresasEnElOrdenPedidoSinDatosPrivados() throws Exception {
        UUID andina = company("andina@example.com", "100", "Servicios Andina");
        UUID norte = company("norte@example.com", "200", "Reparaciones del Norte");

        find(List.of(norte, andina, norte))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.profiles", hasSize(2)))
                .andExpect(jsonPath("$.profiles[*].companyAdminUserId", contains(norte.toString(), andina.toString())))
                .andExpect(jsonPath("$.profiles[*].name", contains("Reparaciones del Norte", "Servicios Andina")))
                .andExpect(content().string(not(containsString("900100"))))
                .andExpect(content().string(not(containsString("andina@example.com"))))
                .andExpect(content().string(not(containsString("3104445566"))));
    }

    @Test
    void omiteCuentasQueNoSonDeUnaEmpresaActiva() throws Exception {
        UUID active = company("andina@example.com", "100", "Servicios Andina");
        UUID disabled = company("cerrada@example.com", "300", "Empresa Cerrada");
        transactionTemplate.executeWithoutResult(status -> userRepository.findById(disabled).orElseThrow().disable());
        UUID withoutCompany = account("sola@example.com", "400", Role.COMPANY).getId();
        UUID technician = account("tecnico@example.com", "500", Role.PROFESSIONAL).getId();

        find(List.of(disabled, withoutCompany, technician, UUID.randomUUID(), active))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles[*].companyAdminUserId", contains(active.toString())));
    }

    // ---------- Validación y seguridad ----------

    @Test
    void unaListaVaciaOConMasDeCienEmpresasResponde400() throws Exception {
        find(List.of()).andExpect(status().isBadRequest());

        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(UUID.randomUUID());
        }
        find(tooMany).andExpect(status().isBadRequest());
    }

    @Test
    void sinLaClaveInternaNoResponde() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("companyAdminUserIds",
                List.of(company("andina@example.com", "100", "Servicios Andina"))));

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH).header("X-Internal-Api-Key", "clave-interna-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles", hasSize(1)));
    }
}
