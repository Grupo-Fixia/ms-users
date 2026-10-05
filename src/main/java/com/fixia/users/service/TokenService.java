package com.fixia.users.service;

import com.fixia.users.config.JwtProperties;
import com.fixia.users.domain.RefreshToken;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.dto.TokenResponse;
import com.fixia.users.exception.InvalidRefreshTokenException;
import com.fixia.users.repository.RefreshTokenRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Emisión de access tokens (JWT RS256) y emisión/rotación de refresh tokens persistidos. */
@Service
public class TokenService {

    /** Prefijo del claim "sub" definido en la wiki: usr_<uuid>. */
    public static final String SUBJECT_PREFIX = "usr_";

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder, JwtProperties properties,
                        RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                        Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public TokenResponse issueTokens(User user) {
        Instant now = clock.instant();
        String accessToken = createAccessToken(user, now);
        String refreshToken = createRefreshToken(user, now);
        return new TokenResponse(accessToken, refreshToken, TokenResponse.BEARER,
                properties.accessTokenTtl().toSeconds());
    }

    /** Canjea un refresh token por un par nuevo. El token presentado queda revocado: es de un solo uso. */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .filter(token -> token.isUsable(now))
                .orElseThrow(InvalidRefreshTokenException::new);

        User user = userRepository.findById(stored.getUserId())
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (refreshTokenRepository.revokeIfActive(stored.getId(), now) == 0) {
            throw new InvalidRefreshTokenException();
        }
        return issueTokens(user);
    }

    private String createAccessToken(User user, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(SUBJECT_PREFIX + user.getId())
                .audience(List.of(properties.audience()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of("ROLE_" + user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String createRefreshToken(User user, Instant now) {
        byte[] random = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        refreshTokenRepository.save(new RefreshToken(
                user.getId(),
                TokenHasher.sha256Hex(rawToken),
                now.plus(properties.refreshTokenTtl()),
                now));
        return rawToken;
    }
}
