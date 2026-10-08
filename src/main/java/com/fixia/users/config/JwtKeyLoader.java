package com.fixia.users.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Carga o genera el par de claves RSA con el que se firman y verifican los tokens. */
final class JwtKeyLoader {

    private static final int GENERATED_KEY_SIZE = 2048;

    private JwtKeyLoader() {
    }

    /** Par de claves PKCS#8 (privada) y X.509 (pública) en formato PEM. */
    static RSAKey fromPem(String privatePem, String publicPem) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(decodePem(privatePem)));
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new X509EncodedKeySpec(decodePem(publicPem)));
            return build(publicKey, privateKey);
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            // No se incluye el contenido de la clave en el mensaje.
            throw new IllegalStateException(
                    "Las claves JWT no tienen un formato PEM válido (PKCS#8 la privada y X.509 la pública)");
        }
    }

    /** Par efímero, solo para los perfiles local y test: los tokens dejan de valer al reiniciar. */
    static RSAKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(GENERATED_KEY_SIZE);
            KeyPair pair = generator.generateKeyPair();
            return build((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo generar el par de claves JWT", e);
        }
    }

    private static RSAKey build(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        try {
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo calcular el identificador de la clave JWT");
        }
    }

    private static byte[] decodePem(String pem) {
        // Las variables de entorno suelen traer los saltos de línea como "\n" literales.
        String normalized = pem.replace("\\n", "\n");
        StringBuilder body = new StringBuilder();
        for (String line : normalized.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("-----")) {
                body.append(trimmed);
            }
        }
        return Base64.getDecoder().decode(body.toString());
    }
}
