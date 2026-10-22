package com.fixia.users.service;

import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.dto.ClientProfileRequest;
import com.fixia.users.exception.InvalidClientDataException;
import com.fixia.users.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Perfil del cliente autenticado (GC-317). Siempre se resuelve por el usuario del token, nunca por un identificador
 * recibido en la petición: así un cliente no puede leer ni modificar el perfil de otro. El perfil son los datos de la
 * cuenta (GC-318), por lo que no hay tabla ni datos duplicados.
 */
@Service
public class ClientProfileService {

    private final UserRepository userRepository;

    public ClientProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return findActiveClient(userId);
    }

    /**
     * Reemplaza nombre, apellido y teléfono. El cuerpo ya viene validado con las reglas del registro; si aun así
     * algún dato no cumple las del dominio, la cuenta queda sin cambios.
     */
    @Transactional
    public User update(UUID userId, ClientProfileRequest request) {
        User client = findActiveClient(userId);
        try {
            client.updateContactInfo(request.firstName(), request.lastName(), request.phone());
        } catch (IllegalArgumentException e) {
            throw new InvalidClientDataException(e.getMessage(), e);
        }
        return userRepository.saveAndFlush(client);
    }

    /**
     * Igual que GET /api/users/me: si la cuenta ya no existe, está deshabilitada o no es de un cliente (por ejemplo,
     * un técnico), la sesión no sirve para esta ruta y la respuesta es el mismo 401 de cualquier token inválido.
     */
    private User findActiveClient(UUID userId) {
        return userRepository.findByIdAndRole(userId, Role.CLIENT)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("Sesión no válida"));
    }
}
