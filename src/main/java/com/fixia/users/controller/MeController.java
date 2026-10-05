package com.fixia.users.controller;

import com.fixia.users.domain.UserStatus;
import com.fixia.users.dto.UserProfileResponse;
import com.fixia.users.repository.UserRepository;
import com.fixia.users.service.TokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
public class MeController {

    private final UserRepository userRepository;

    public MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Perfil del usuario autenticado. Sirve como ejemplo de ruta privada y para comprobar la sesión.
     * Si el token es válido pero la cuenta ya no existe o está deshabilitada, la excepción de autenticación
     * llega al mismo punto de entrada que cualquier otro token inválido: el 401 es idéntico.
     */
    @GetMapping
    public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = parseUserId(jwt.getSubject());
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(UserProfileResponse::from)
                .orElseThrow(MeController::sessionNoLongerValid);
    }

    private static UUID parseUserId(String subject) {
        try {
            return TokenService.parseUserId(subject);
        } catch (RuntimeException e) {
            throw sessionNoLongerValid();
        }
    }

    private static AuthenticationException sessionNoLongerValid() {
        return new BadCredentialsException("Sesión no válida");
    }
}
