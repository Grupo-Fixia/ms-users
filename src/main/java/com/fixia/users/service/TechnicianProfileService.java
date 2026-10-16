package com.fixia.users.service;

import com.fixia.users.domain.AvailabilitySlot;
import com.fixia.users.domain.CoverageZone;
import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.dto.AvailabilitySlotRequest;
import com.fixia.users.dto.CoverageZoneRequest;
import com.fixia.users.dto.TechnicianProfileRequest;
import com.fixia.users.exception.InvalidTechnicianDataException;
import com.fixia.users.exception.TechnicianProfileNotFoundException;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
        Technician technician = lockTechnicianOfActiveUser(userId);
        technician.updateProfile(
                request.professionalDescription().trim(),
                request.yearsOfExperience(),
                request.categories(),
                clock.instant());
        return technicianRepository.saveAndFlush(technician);
    }

    /** Reemplaza solo las especialidades y servicios ofrecidos (RF-010); el resto del perfil no cambia. */
    @Transactional
    public Technician updateCategories(UUID userId, Set<ServiceCategory> categories) {
        Technician technician = lockTechnicianOfActiveUser(userId);
        technician.replaceCategories(categories, clock.instant());
        return technicianRepository.saveAndFlush(technician);
    }

    /** Igual que GET /api/users/me: si la cuenta ya no existe o está deshabilitada, la sesión deja de valer (401). */
    /** Declara o reemplaza la zona de atención (RF-011). */
    @Transactional
    public Technician updateCoverageZone(UUID userId, CoverageZoneRequest request) {
        Technician technician = lockTechnicianOfActiveUser(userId);
        technician.updateCoverageZone(toCoverageZone(request), clock.instant());
        return technicianRepository.saveAndFlush(technician);
    }

    /** Retira la zona de atención. */
    @Transactional
    public Technician clearCoverageZone(UUID userId) {
        Technician technician = lockTechnicianOfActiveUser(userId);
        technician.updateCoverageZone(null, clock.instant());
        return technicianRepository.saveAndFlush(technician);
    }

    /** Reemplaza la disponibilidad semanal (RF-011); las franjas repetidas cuentan una sola vez. */
    @Transactional
    public Technician replaceAvailability(UUID userId, List<AvailabilitySlotRequest> slots) {
        Technician technician = lockTechnicianOfActiveUser(userId);
        Set<AvailabilitySlot> newSlots = slots.stream()
                .map(slot -> new AvailabilitySlot(slot.dayOfWeek(), slot.startTime(), slot.endTime()))
                .collect(Collectors.toSet());
        technician.replaceAvailability(newSlots, clock.instant());
        return technicianRepository.saveAndFlush(technician);
    }

    /** Sin coordenadas ni radio la zona es toda la ciudad; con alguno de los tres, deben venir los tres. */
    private static CoverageZone toCoverageZone(CoverageZoneRequest request) {
        if (request.latitude() == null && request.longitude() == null && request.radiusKm() == null) {
            return new CoverageZone(request.city());
        }
        if (request.latitude() == null || request.longitude() == null || request.radiusKm() == null) {
            throw new InvalidTechnicianDataException("El círculo de la zona necesita latitud, longitud y radio");
        }
        return new CoverageZone(request.city(), request.latitude(), request.longitude(), request.radiusKm());
    }

    private Technician findTechnicianOfActiveUser(UUID userId) {
        requireActiveUser(userId);
        return technicianRepository.findByUserId(userId).orElseThrow(TechnicianProfileNotFoundException::new);
    }

    /** Igual que la anterior, pero bloquea al técnico: es la que usan las escrituras (ver el repositorio). */
    private Technician lockTechnicianOfActiveUser(UUID userId) {
        requireActiveUser(userId);
        return technicianRepository.findByUserIdForUpdate(userId)
                .orElseThrow(TechnicianProfileNotFoundException::new);
    }

    private void requireActiveUser(UUID userId) {
        userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("Sesión no válida"));
    }
}
