package com.fixia.users.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    /** Tolerancia mínima entre relojes; el valor por defecto de Spring (60 s) alarga demasiado un token vencido. */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(5);

    @Bean
    public RSAKey rsaKey(JwtProperties properties, Environment environment) {
        if (properties.hasPrivateKey() && properties.hasPublicKey()) {
            return JwtKeyLoader.fromPem(properties.privateKey(), properties.publicKey());
        }
        if (properties.hasPrivateKey() || properties.hasPublicKey()) {
            throw new IllegalStateException("Debe definir JWT_PRIVATE_KEY y JWT_PUBLIC_KEY juntas");
        }
        if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
            log.warn("Sin claves JWT configuradas: se usa un par efímero (solo para los perfiles local y test)");
            return JwtKeyLoader.generate();
        }
        throw new IllegalStateException(
                "JWT_PRIVATE_KEY y JWT_PUBLIC_KEY son obligatorias fuera de los perfiles local y test");
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    /**
     * Verifica firma RS256, expiración, emisor y audiencia. Cualquier otro
     * {@code OAuth2TokenValidator<Jwt>} registrado como bean se suma a la validación.
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey, JwtProperties properties,
                                 ObjectProvider<OAuth2TokenValidator<Jwt>> additionalValidators) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(rsaKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator(CLOCK_SKEW));
        validators.add(new JwtIssuerValidator(properties.issuer()));
        validators.add(audienceValidator(properties.audience()));
        additionalValidators.orderedStream().forEach(validators::add);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        OAuth2Error error = new OAuth2Error("invalid_token", "Audiencia no válida", null);
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }
}
