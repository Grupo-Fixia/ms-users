package com.fixia.users.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Descifra en memoria contraseñas enviadas desde el Frontend con cifrado de payload
 * ({@code ENC:v1:<nonce_hex>:<ciphertext_base64>}) para que la contraseña no sea visible en texto plano
 * en el inspector de red (F12) del navegador antes de llegar a la comparación BCrypt costo 12.
 *
 * <p>Si el valor recibido no tiene el prefijo {@code ENC:v1:}, lo devuelve intacto para mantener
 * compatibilidad total con clientes API directos y pruebas automatizadas.
 */
public final class CredentialCipher {

    public static final String PREFIX = "ENC:v1:";
    private static final String TRANSPORT_SECRET = "FixiaTransportKey#2026!ProdV4";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialCipher() {
    }

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.startsWith(PREFIX)) {
            return plainText;
        }
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        String nonceHex = HexFormat.of().formatHex(nonce);
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] cipherBytes = xorWithSha256Keystream(plainBytes, nonceHex);
        return PREFIX + nonceHex + ":" + Base64.getEncoder().encodeToString(cipherBytes);
    }

    public static String decryptIfEncrypted(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            return value;
        }
        String rest = value.substring(PREFIX.length());
        int sep = rest.indexOf(':');
        if (sep <= 0 || sep == rest.length() - 1) {
            return value;
        }
        String nonceHex = rest.substring(0, sep);
        String cipherB64 = rest.substring(sep + 1);
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(cipherB64);
            byte[] plainBytes = xorWithSha256Keystream(cipherBytes, nonceHex);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static byte[] xorWithSha256Keystream(byte[] input, String nonceHex) {
        byte[] output = new byte[input.length];
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] block = null;
            for (int i = 0; i < input.length; i++) {
                if (i % 32 == 0) {
                    String seed = TRANSPORT_SECRET + ":" + nonceHex + ":" + (i / 32);
                    block = sha256.digest(seed.getBytes(StandardCharsets.UTF_8));
                }
                output[i] = (byte) (input[i] ^ block[i % 32]);
            }
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
