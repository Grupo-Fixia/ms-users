package com.fixia.users.service;

import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.dto.LoginRequest;
import com.fixia.users.dto.TokenResponse;
import com.fixia.users.exception.InvalidCredentialsException;
import com.fixia.users.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /** Hash de una contraseña aleatoria: permite comparar siempre, exista o no el correo. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<User> found = userRepository.findByEmail(email);

        // La comparación BCrypt se hace siempre, para que el tiempo de respuesta no delate si el correo existe.
        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (found.isEmpty() || !passwordMatches || found.get().getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
        return tokenService.issueTokens(found.get());
    }

    public TokenResponse refresh(String refreshToken) {
        return tokenService.refresh(refreshToken);
    }
}
