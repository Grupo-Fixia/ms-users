package com.fixia.users.service;

import com.fixia.users.domain.CoverageZone;
import com.fixia.users.domain.IneligibilityReason;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.domain.VerificationStatus;
import com.fixia.users.dto.EligibilityRequest;
import com.fixia.users.dto.EligibilityResponse;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Decide si un técnico puede atender una o varias solicitudes de servicio (RF-011 y RF-010). Los datos del técnico
 * viven en esta base, por eso la regla se evalúa aquí y ms-services solo consulta el resultado (GC-342 y GC-337).
 *
 * <p>Un técnico es elegible si la categoría de la solicitud está entre sus especialidades, su zona de atención
 * cubre la ubicación aproximada ({@link CoverageZone#covers}) y el momento esperado cae en alguna de sus franjas
 * ({@link Technician#isAvailableAt}). La verificación vigente (RN-001) solo se exige si
 * {@code fixia.eligibility.require-verified} está activo: hoy no existe el flujo de verificación y todos los
 * técnicos nacen en {@code PENDING}.
 */
@Service
public class TechnicianEligibilityService {

    private final UserRepository userRepository;
    private final TechnicianRepository technicianRepository;
    private final boolean requireVerified;

    public TechnicianEligibilityService(UserRepository userRepository, TechnicianRepository technicianRepository,
                                        @Value("${fixia.eligibility.require-verified:false}") boolean requireVerified) {
        this.userRepository = userRepository;
        this.technicianRepository = technicianRepository;
        this.requireVerified = requireVerified;
    }

    @Transactional(readOnly = true)
    public EligibilityResponse evaluate(UUID userId, List<EligibilityRequest.RequestData> requests) {
        Optional<Technician> technician = activeTechnician(userId);
        List<EligibilityResponse.Result> results = new ArrayList<>();
        for (EligibilityRequest.RequestData request : requests) {
            List<IneligibilityReason> reasons = technician
                    .map(found -> reasons(found, request))
                    .orElse(List.of(IneligibilityReason.TECHNICIAN_NOT_FOUND));
            results.add(new EligibilityResponse.Result(request.requestId(), reasons.isEmpty(), reasons));
        }
        return new EligibilityResponse(results);
    }

    /** Solo cuenta un técnico cuya cuenta sigue activa; si no, ninguna solicitud es elegible. */
    private Optional<Technician> activeTechnician(UUID userId) {
        boolean active = userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .isPresent();
        return active ? technicianRepository.findByUserId(userId) : Optional.empty();
    }

    private List<IneligibilityReason> reasons(Technician technician, EligibilityRequest.RequestData request) {
        List<IneligibilityReason> reasons = new ArrayList<>();
        if (requireVerified && technician.getVerificationStatus() != VerificationStatus.VALID) {
            reasons.add(IneligibilityReason.VERIFICATION);
        }
        if (!technician.getCategories().contains(request.category())) {
            reasons.add(IneligibilityReason.CATEGORY);
        }
        CoverageZone zone = technician.getCoverageZone();
        if (zone == null || !zone.covers(request.city(), request.latitude(), request.longitude())) {
            reasons.add(IneligibilityReason.ZONE);
        }
        if (!technician.isAvailableAt(request.expectedAt())) {
            reasons.add(IneligibilityReason.AVAILABILITY);
        }
        return reasons;
    }
}
