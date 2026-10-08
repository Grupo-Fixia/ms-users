package com.fixia.users.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuración de los tokens. Las claves RS256 llegan como PEM por variables de entorno
 * (JWT_PRIVATE_KEY y JWT_PUBLIC_KEY); nunca se guardan en el repositorio.
 */
@ConfigurationProperties(prefix = "fixia.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String privateKey,
        String publicKey
) {

    boolean hasPrivateKey() {
        return privateKey != null && !privateKey.isBlank();
    }

    boolean hasPublicKey() {
        return publicKey != null && !publicKey.isBlank();
    }

    /** Evita que la clave privada aparezca en logs si el objeto se imprime. */
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", audience=" + audience + "]";
    }
}
