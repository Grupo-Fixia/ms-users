package com.fixia.users.service;

import com.fixia.users.domain.Role;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.dto.TechnicianRegistrationRequest;
import com.fixia.users.exception.AccountAlreadyExistsException;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class TechnicianRegistrationService {

    private final UserRepository userRepository;
    private final TechnicianRepository technicianRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public TechnicianRegistrationService(UserRepository userRepository, TechnicianRepository technicianRepository,
                                         PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.technicianRepository = technicianRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public record Registration(User user, Technician technician) {
    }

    /** Crea la cuenta base con rol PROFESSIONAL y su especialización de técnico en la misma transacción. */
    @Transactional
    public Registration register(TechnicianRegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String documentNumber = request.documentNumber().trim().toUpperCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)
                || userRepository.existsByDocumentTypeAndDocumentNumber(request.documentType(), documentNumber)) {
            throw new AccountAlreadyExistsException();
        }

        Instant now = clock.instant();
        User user = userRepository.saveAndFlush(new User(
                email,
                passwordEncoder.encode(request.password()),
                Role.PROFESSIONAL,
                request.firstName().trim(),
                request.lastName().trim(),
                request.documentType(),
                documentNumber,
                request.phone().trim(),
                request.policyVersion().trim(),
                now));
        Technician technician = technicianRepository.saveAndFlush(new Technician(user.getId(), now));
        return new Registration(user, technician);
    }
}
