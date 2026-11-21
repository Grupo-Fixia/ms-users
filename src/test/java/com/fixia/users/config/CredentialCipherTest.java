package com.fixia.users.config;

import com.fixia.users.dto.LoginRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialCipherTest {

    @Test
    void cifraYDescifraContrasenaConNonceAleatorio() {
        String plain = "FixiaQA#2026";
        String enc1 = CredentialCipher.encrypt(plain);
        String enc2 = CredentialCipher.encrypt(plain);

        assertThat(enc1).startsWith("ENC:v1:");
        assertThat(enc2).startsWith("ENC:v1:");
        assertThat(enc1).isNotEqualTo(enc2);
        assertThat(enc1).doesNotContain(plain);

        assertThat(CredentialCipher.decryptIfEncrypted(enc1)).isEqualTo(plain);
        assertThat(CredentialCipher.decryptIfEncrypted(enc2)).isEqualTo(plain);

        LoginRequest req = new LoginRequest("admin@fixia-qa.test", enc1);
        assertThat(req.password()).isEqualTo(plain);
    }

    @Test
    void mantieneIntactaContrasenaNoCifrada() {
        assertThat(CredentialCipher.decryptIfEncrypted("FixiaQA#2026")).isEqualTo("FixiaQA#2026");
        assertThat(CredentialCipher.decryptIfEncrypted(null)).isNull();
    }
}
