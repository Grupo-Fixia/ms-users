package com.fixia.users.controller;

import com.fixia.users.dto.TechnicianRegistrationRequest;
import com.fixia.users.dto.TechnicianRegistrationResponse;
import com.fixia.users.service.TechnicianRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/technicians")
public class TechnicianRegistrationController {

    private final TechnicianRegistrationService registrationService;

    public TechnicianRegistrationController(TechnicianRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    public ResponseEntity<TechnicianRegistrationResponse> register(
            @Valid @RequestBody TechnicianRegistrationRequest request) {
        TechnicianRegistrationService.Registration registration = registrationService.register(request);
        TechnicianRegistrationResponse response =
                TechnicianRegistrationResponse.from(registration.user(), registration.technician());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
