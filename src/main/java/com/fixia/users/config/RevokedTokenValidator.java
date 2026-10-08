package com.fixia.users.config;

import com.fixia.users.repository.RevokedTokenRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Rechaza los access tokens cerrados con logout. JwtConfig lo suma a la validación del decoder, de modo que
 * el token deja de servir de inmediato, sin esperar a su expiración.
 */
@Component
public class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "Token revocado", null);

    private final RevokedTokenRepository revokedTokenRepository;

    public RevokedTokenValidator(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        // Sin "jti" no se puede comprobar la revocación: nuestros tokens siempre lo incluyen.
        if (jwt.getId() == null || revokedTokenRepository.existsById(jwt.getId())) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
