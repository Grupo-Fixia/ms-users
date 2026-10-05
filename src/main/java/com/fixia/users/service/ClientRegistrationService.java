package com.fixia.users.service;

import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.dto.ClientRegistrationRequest;
import com.fixia.users.exception.AccountAlreadyExistsException;
import com.fixia.users.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class ClientRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ClientRegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public User register(ClientRegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String documentNumber = request.documentNumber().trim().toUpperCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)
                || userRepository.existsByDocumentTypeAndDocumentNumber(request.documentType(), documentNumber)) {
            throw new AccountAlreadyExistsException();
        }

        Instant now = clock.instant();
        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                Role.CLIENT,
                request.firstName().trim(),
                request.lastName().trim(),
                request.documentType(),
                documentNumber,
                request.phone().trim(),
                request.policyVersion().trim(),
                now);
        return userRepository.saveAndFlush(user);
    }
}
