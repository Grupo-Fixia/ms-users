package com.fixia.users.controller;

import com.fixia.users.dto.ClientRegistrationRequest;
import com.fixia.users.dto.ClientRegistrationResponse;
import com.fixia.users.service.ClientRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/clients")
public class ClientRegistrationController {

    private final ClientRegistrationService registrationService;

    public ClientRegistrationController(ClientRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    public ResponseEntity<ClientRegistrationResponse> register(@Valid @RequestBody ClientRegistrationRequest request) {
        ClientRegistrationResponse response = ClientRegistrationResponse.from(registrationService.register(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
