package com.fixia.users.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Edición del perfil del titular (GC-293 / GC-318): solo nombre, apellido y teléfono. Pruebas de dominio puro.
 */
class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private static User client() {
        return new User("ana@example.com", "hash-bcrypt", Role.CLIENT, "Ana", "Pérez",
                DocumentType.CC, "1020304050", "+573001112233", "v1.0", NOW);
    }

    @Test
    void actualizaNombreApellidoYTelefono() {
        User user = client();

        user.updateContactInfo("María", "Gómez", "+573109998877");

        assertThat(user.getFirstName()).isEqualTo("María");
        assertThat(user.getLastName()).isEqualTo("Gómez");
        assertThat(user.getPhone()).isEqualTo("+573109998877");
    }

    @Test
    void guardaLosValoresSinEspaciosAlInicioNiAlFinal() {
        User user = client();

        user.updateContactInfo("  María ", " Gómez  ", " 3109998877 ");

        assertThat(user.getFirstName()).isEqualTo("María");
        assertThat(user.getLastName()).isEqualTo("Gómez");
        assertThat(user.getPhone()).isEqualTo("3109998877");
    }

    @Test
    void noTocaLaIdentidadNiLasCredencialesNiElConsentimiento() {
        User user = client();

        user.updateContactInfo("María", "Gómez", "3109998877");

        assertThat(user.getEmail()).isEqualTo("ana@example.com");
        assertThat(user.getDocumentType()).isEqualTo(DocumentType.CC);
        assertThat(user.getDocumentNumber()).isEqualTo("1020304050");
        assertThat(user.getPasswordHash()).isEqualTo("hash-bcrypt");
        assertThat(user.getRole()).isEqualTo(Role.CLIENT);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getConsentPolicyVersion()).isEqualTo("v1.0");
        assertThat(user.getConsentAcceptedAt()).isEqualTo(NOW);
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void elNombreElApellidoYElTelefonoSonObligatorios(String blank) {
        User user = client();

        assertThatThrownBy(() -> user.updateContactInfo(blank, "Gómez", "3109998877"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El nombre es obligatorio");
        assertThatThrownBy(() -> user.updateContactInfo("María", blank, "3109998877"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El apellido es obligatorio");
        assertThatThrownBy(() -> user.updateContactInfo("María", "Gómez", blank))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El teléfono es obligatorio");
    }

    @Test
    void rechazaTextosMasLargosQueLaColumna() {
        User user = client();
        String tooLong = "x".repeat(User.NAME_MAX_LENGTH + 1);

        assertThatThrownBy(() -> user.updateContactInfo(tooLong, "Gómez", "3109998877"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre no puede superar 100 caracteres");
        assertThatThrownBy(() -> user.updateContactInfo("María", tooLong, "3109998877"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El apellido no puede superar 100 caracteres");
        assertThatThrownBy(() -> user.updateContactInfo("María", "Gómez", "+5731099988771234567890"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El teléfono no puede superar 20 caracteres");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc1234567", "310-999-8877", "+57 310 9998877", "123456", "+1234567890123456", "++573109998877"})
    void rechazaTelefonosConFormatoInvalido(String phone) {
        User user = client();

        assertThatThrownBy(() -> user.updateContactInfo("María", "Gómez", phone))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El teléfono no es válido");
    }

    @ParameterizedTest
    @ValueSource(strings = {"3109998877", "+573109998877", "1234567", "+123456789012345"})
    void aceptaTelefonosEnFormatoValido(String phone) {
        User user = client();

        user.updateContactInfo("María", "Gómez", phone);

        assertThat(user.getPhone()).isEqualTo(phone);
    }

    @Test
    void siUnDatoEsInvalidoLaCuentaNoQuedaAMediasActualizar() {
        User user = client();

        assertThatThrownBy(() -> user.updateContactInfo("María", "Gómez", "no-es-telefono"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.getFirstName()).isEqualTo("Ana");
        assertThat(user.getLastName()).isEqualTo("Pérez");
        assertThat(user.getPhone()).isEqualTo("+573001112233");
    }
}
