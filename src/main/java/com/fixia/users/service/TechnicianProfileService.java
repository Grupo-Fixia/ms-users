package com.fixia.users.service;

import com.fixia.users.domain.Technician;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.dto.TechnicianProfileRequest;
import com.fixia.users.exception.TechnicianProfileNotFoundException;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Información profesional del técnico autenticado. Siempre se resuelve por el usuario del token, nunca por un
 * identificador recibido en la petición: así un usuario no puede leer ni modificar el perfil de otro técnico.
 */
@Service
public class TechnicianProfileService {

    private final UserRepository userRepository;
    private final TechnicianRepository technicianRepository;
    private final Clock clock;

    public TechnicianProfileService(UserRepository userRepository, TechnicianRepository technicianRepository,
                                    Clock clock) {
        this.userRepository = userRepository;
        this.technicianRepository = technicianRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Technician get(UUID userId) {
        return findTechnicianOfActiveUser(userId);
    }

    @Transactional
    public Technician update(UUID userId, TechnicianProfileRequest request) {
        Technician technician = findTechnicianOfActiveUser(userId);
        technician.updateProfile(
                request.professionalDescription().trim(),
                request.yearsOfExperience(),
                request.categories(),
                clock.instant());
        return technicianRepository.saveAndFlush(technician);
    }

    /** Igual que GET /api/users/me: si la cuenta ya no existe o está deshabilitada, la sesión deja de valer (401). */
    private Technician findTechnicianOfActiveUser(UUID userId) {
        userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("Sesión no válida"));
        return technicianRepository.findByUserId(userId).orElseThrow(TechnicianProfileNotFoundException::new);
    }
}
