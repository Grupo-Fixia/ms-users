package com.fixia.users.repository;

import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistencia del perfil del cliente (GC-318): vive en la cuenta ({@code users}), así que editarlo no puede
 * desalinearse de los datos de la cuenta. Cada paso corre en su propia transacción, como lo hará el servicio.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-10-01T10:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
    }

    private User save(String email, String document, Role role) {
        return userRepository.saveAndFlush(new User(email, "hash-bcrypt", role, "Ana", "Pérez",
                DocumentType.CC, document, "+573001112233", "v1.0", REGISTERED_AT));
    }

    private void updateContactInfo(UUID id, String firstName, String lastName, String phone) {
        transactionTemplate.executeWithoutResult(status -> userRepository.findById(id).orElseThrow()
                .updateContactInfo(firstName, lastName, phone));
    }

    @Test
    void loCambiadoQuedaPersistidoYSeMuestraAlVolverAConsultar() {
        UUID id = save("ana@example.com", "1020304050", Role.CLIENT).getId();

        updateContactInfo(id, "María", "Gómez", "+573109998877");

        User reloaded = userRepository.findById(id).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("María");
        assertThat(reloaded.getLastName()).isEqualTo("Gómez");
        assertThat(reloaded.getPhone()).isEqualTo("+573109998877");
    }

    @Test
    void laEdicionNoAlteraLaIdentidadLasCredencialesNiElConsentimiento() {
        UUID id = save("ana@example.com", "1020304050", Role.CLIENT).getId();

        updateContactInfo(id, "María", "Gómez", "+573109998877");

        User reloaded = userRepository.findById(id).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("ana@example.com");
        assertThat(reloaded.getDocumentType()).isEqualTo(DocumentType.CC);
        assertThat(reloaded.getDocumentNumber()).isEqualTo("1020304050");
        assertThat(reloaded.getPasswordHash()).isEqualTo("hash-bcrypt");
        assertThat(reloaded.getRole()).isEqualTo(Role.CLIENT);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloaded.getConsentPolicyVersion()).isEqualTo("v1.0");
        assertThat(reloaded.getConsentAcceptedAt()).isEqualTo(REGISTERED_AT);
        assertThat(reloaded.getCreatedAt()).isEqualTo(REGISTERED_AT);
    }

    @Test
    void unaEdicionInvalidaNoCambiaNadaEnLaBase() {
        UUID id = save("ana@example.com", "1020304050", Role.CLIENT).getId();

        assertThatThrownBy(() -> updateContactInfo(id, "María", "Gómez", "no-es-telefono"))
                .isInstanceOf(IllegalArgumentException.class);

        User reloaded = userRepository.findById(id).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("Ana");
        assertThat(reloaded.getLastName()).isEqualTo("Pérez");
        assertThat(reloaded.getPhone()).isEqualTo("+573001112233");
    }

    @Test
    void editarUnaCuentaNoAfectaALasDemas() {
        UUID ana = save("ana@example.com", "1020304050", Role.CLIENT).getId();
        UUID beto = save("beto@example.com", "1020304051", Role.CLIENT).getId();

        updateContactInfo(ana, "María", "Gómez", "+573109998877");

        User untouched = userRepository.findById(beto).orElseThrow();
        assertThat(untouched.getFirstName()).isEqualTo("Ana");
        assertThat(untouched.getPhone()).isEqualTo("+573001112233");
    }

    @Test
    void elTelefonoNoEsUnicoPeroElCorreoYElDocumentoSiLoSiguenSiendo() {
        save("ana@example.com", "1020304050", Role.CLIENT);
        UUID beto = save("beto@example.com", "1020304051", Role.CLIENT).getId();

        // Dos cuentas pueden compartir teléfono (por ejemplo, un hogar): no es una restricción de identidad.
        updateContactInfo(beto, "Beto", "Ruiz", "+573001112233");
        assertThat(userRepository.findById(beto).orElseThrow().getPhone()).isEqualTo("+573001112233");

        assertThatThrownBy(() -> save("ana@example.com", "9999999999", Role.CLIENT))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> save("otro@example.com", "1020304050", Role.CLIENT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByIdAndRoleSoloDevuelveLaCuentaDelRolIndicado() {
        UUID client = save("ana@example.com", "1020304050", Role.CLIENT).getId();
        UUID technician = save("tec@example.com", "1020304052", Role.PROFESSIONAL).getId();

        assertThat(userRepository.findByIdAndRole(client, Role.CLIENT)).isPresent();
        assertThat(userRepository.findByIdAndRole(technician, Role.CLIENT)).isEmpty();
        assertThat(userRepository.findByIdAndRole(UUID.randomUUID(), Role.CLIENT)).isEmpty();
    }
}
