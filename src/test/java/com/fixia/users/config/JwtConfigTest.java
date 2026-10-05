package com.fixia.users.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    private static JwtProperties properties(String privateKey, String publicKey) {
        return new JwtProperties("https://auth.fixia.com", "https://api.fixia.com",
                Duration.ofMinutes(15), Duration.ofDays(7), privateKey, publicKey);
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String pem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    @Test
    void cargaElParDeClavesDesdePem() throws Exception {
        KeyPair pair = newKeyPair();
        JwtProperties props = properties(pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", pair.getPublic().getEncoded()));

        RSAKey key = config.rsaKey(props, new MockEnvironment().withProperty("x", "y"));

        assertThat(key.toRSAPublicKey().getEncoded()).isEqualTo(pair.getPublic().getEncoded());
        assertThat(key.isPrivate()).isTrue();
        assertThat(key.getKeyID()).isNotBlank();
    }

    @Test
    void aceptaSaltosDeLineaEscritosComoBarraN() throws Exception {
        KeyPair pair = newKeyPair();
        String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded()).replace("\n", "\\n");
        String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded()).replace("\n", "\\n");

        RSAKey key = config.rsaKey(properties(privatePem, publicPem), new MockEnvironment());

        assertThat(key.toRSAPublicKey().getEncoded()).isEqualTo(pair.getPublic().getEncoded());
    }

    @Test
    void unPemInvalidoFallaSinMostrarElContenidoDeLaClave() {
        String secret = "ESTO-NO-ES-UNA-CLAVE-SECRETA";

        assertThatThrownBy(() -> config.rsaKey(properties(secret, secret), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PEM válido")
                .hasMessageNotContaining(secret);
    }

    @Test
    void definirSoloUnaDeLasDosClavesFalla() throws Exception {
        KeyPair pair = newKeyPair();
        String onlyPublic = pem("PUBLIC KEY", pair.getPublic().getEncoded());

        assertThatThrownBy(() -> config.rsaKey(properties("", onlyPublic), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("juntas");
    }

    @Test
    void fueraDeLosPerfilesLocalYTestSinClavesElArranqueFalla() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> config.rsaKey(properties("", ""), prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("obligatorias");
        assertThatThrownBy(() -> config.rsaKey(properties(null, null), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enLosPerfilesLocalYTestSeGeneraUnParEfimero() {
        for (String profile : new String[]{"local", "test"}) {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles(profile);

            RSAKey key = config.rsaKey(properties("", ""), env);

            assertThat(key.isPrivate()).isTrue();
            assertThat(key.size()).isEqualTo(2048);
        }
    }

    @Test
    void laClavePrivadaNoApareceAlImprimirLasPropiedades() {
        JwtProperties props = properties("clave-privada-secreta", "clave-publica");

        assertThat(props.toString()).doesNotContain("clave-privada-secreta");
    }
}
