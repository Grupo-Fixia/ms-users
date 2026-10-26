package com.fixia.users.service;

import com.fixia.users.domain.CoverageZone;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.dto.PublicProfilesResponse;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Perfil público de los técnicos para otros microservicios (GC-425): ms-services lo pide para mostrarle al cliente
 * quién se postuló a su solicitud. Los datos del técnico viven en esta base (Database per Service), por eso se arman
 * aquí y ms-services solo los consulta, por lotes, sin leer esta base.
 */
@Service
public class TechnicianPublicProfileService {

    private final UserRepository userRepository;
    private final TechnicianRepository technicianRepository;

    public TechnicianPublicProfileService(UserRepository userRepository, TechnicianRepository technicianRepository) {
        this.userRepository = userRepository;
        this.technicianRepository = technicianRepository;
    }

    /**
     * Un perfil por cada técnico activo, en el orden pedido y sin repetidos. Se omiten los ids que no son de un
     * técnico con cuenta activa: un cliente, una cuenta deshabilitada o un id inexistente no dan error ni datos.
     */
    @Transactional(readOnly = true)
    public PublicProfilesResponse find(Collection<UUID> userIds) {
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(userIds));
        Map<UUID, User> activeTechnicians = userRepository.findAllById(ids).stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getRole() == Role.PROFESSIONAL)
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Technician> technicians = technicianRepository.findByUserIdIn(activeTechnicians.keySet()).stream()
                .collect(Collectors.toMap(Technician::getUserId, Function.identity()));

        List<PublicProfilesResponse.Profile> profiles = new ArrayList<>();
        for (UUID id : ids) {
            User user = activeTechnicians.get(id);
            Technician technician = technicians.get(id);
            if (user != null && technician != null) {
                profiles.add(profile(user, technician));
            }
        }
        return new PublicProfilesResponse(profiles);
    }

    private static PublicProfilesResponse.Profile profile(User user, Technician technician) {
        CoverageZone zone = technician.getCoverageZone();
        return new PublicProfilesResponse.Profile(
                user.getId(),
                displayName(user.getFirstName(), user.getLastName()),
                technician.getVerificationStatus(),
                List.copyOf(technician.getCategories()),
                technician.getProfessionalDescription(),
                technician.getYearsOfExperience(),
                zone == null ? null : zone.getCity());
    }

    /** Nombre e inicial del apellido ("Carlos G."): identifica al prestador sin exponer el nombre completo. */
    static String displayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        if (last.isEmpty()) {
            return first;
        }
        String initial = last.substring(0, last.offsetByCodePoints(0, 1)) + ".";
        return first.isEmpty() ? initial : first + " " + initial;
    }
}
