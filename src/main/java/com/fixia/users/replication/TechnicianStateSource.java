package com.fixia.users.replication;

import com.fixia.users.domain.AvailabilitySlot;
import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyTechnician;
import com.fixia.users.domain.CoverageZone;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.domain.VerificationStatus;
import com.fixia.users.dto.Reputation;
import com.fixia.users.repository.CompanyRepository;
import com.fixia.users.repository.CompanyTechnicianRepository;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import com.fixia.users.service.ReputationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Estado de cada técnico para los demás servicios (GC-547): lo que ms-services necesita para decidir la elegibilidad,
 * mostrar el perfil público y saber a qué empresa pertenece, y lo que ms-matching-geo necesita para buscar. Reemplaza
 * las rutas internas de elegibilidad, perfiles, búsqueda y pertenencia a empresas.
 *
 * <p>Lleva la zona de atención con su círculo, que revela el domicilio aproximado: por eso solo viaja por un tópico
 * interno y ningún consumidor debe pasarlo a un cliente. Nunca correo, teléfono ni documento. La regla de verificación
 * (RN-001, {@code fixia.eligibility.require-verified}) se resuelve aquí y viaja ya decidida en
 * {@code verificationRequirementMet}.
 */
@Component
public class TechnicianStateSource implements StateSource {

    public static final String STATE_TYPE = "tecnico.estado";
    public static final String TOPIC = "fixia.users.technician-states";

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    record Coverage(String city, Double latitude, Double longitude, Short radiusKm) {
    }

    record Slot(String dayOfWeek, String startTime, String endTime) {
    }

    record TechnicianState(UUID userId, boolean active, String displayName, VerificationStatus verificationStatus,
                           boolean verificationRequirementMet, List<String> categories,
                           String professionalDescription, Short yearsOfExperience, Coverage coverage,
                           List<Slot> availability, UUID companyAdminUserId, Reputation reputation) {
    }

    private final UserRepository users;
    private final TechnicianRepository technicians;
    private final CompanyTechnicianRepository associations;
    private final CompanyRepository companies;
    private final ReputationService reputations;
    private final boolean requireVerified;

    public TechnicianStateSource(UserRepository users, TechnicianRepository technicians,
                                 CompanyTechnicianRepository associations, CompanyRepository companies,
                                 ReputationService reputations,
                                 @Value("${fixia.eligibility.require-verified:false}") boolean requireVerified) {
        this.users = users;
        this.technicians = technicians;
        this.associations = associations;
        this.companies = companies;
        this.reputations = reputations;
        this.requireVerified = requireVerified;
    }

    @Override
    public String stateType() {
        return STATE_TYPE;
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public String aggregateType() {
        return "TechnicianState";
    }

    @Override
    public Map<UUID, Object> currentStates() {
        List<User> professionals = users.findAll().stream().filter(user -> user.getRole() == Role.PROFESSIONAL)
                .toList();
        List<UUID> ids = professionals.stream().map(User::getId).toList();
        Map<UUID, Technician> profiles = technicians.findByUserIdIn(ids).stream()
                .collect(Collectors.toMap(Technician::getUserId, Function.identity()));
        Map<UUID, UUID> adminByCompany = companies.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Company::getAdminUserId));
        Map<UUID, UUID> companyAdminByTechnician = associations.findAll().stream()
                .filter(association -> adminByCompany.containsKey(association.getCompanyId()))
                .collect(Collectors.toMap(CompanyTechnician::getTechnicianUserId,
                        association -> adminByCompany.get(association.getCompanyId()), (a, b) -> a));
        Map<UUID, Reputation> reputationById = reputations.asProvider(profiles.keySet());

        Map<UUID, Object> states = new LinkedHashMap<>();
        for (User user : professionals) {
            Technician technician = profiles.get(user.getId());
            boolean active = user.getStatus() == UserStatus.ACTIVE && technician != null;
            states.put(user.getId(), technician == null
                    ? new TechnicianState(user.getId(), false, null, null, false, List.of(), null, null, null,
                    List.of(), null, null)
                    : state(user, technician, active, companyAdminByTechnician.get(user.getId()),
                    reputationById.get(user.getId())));
        }
        return states;
    }

    private TechnicianState state(User user, Technician technician, boolean active, UUID companyAdminUserId,
                                  Reputation reputation) {
        CoverageZone zone = technician.getCoverageZone();
        Coverage coverage = zone == null ? null
                : new Coverage(zone.getCity(), zone.getLatitude(), zone.getLongitude(), zone.getRadiusKm());
        List<Slot> slots = technician.getAvailability().stream()
                .sorted(Comparator.naturalOrder())
                .map(TechnicianStateSource::slot)
                .toList();
        List<String> categories = technician.getCategories().stream().map(Enum::name).sorted().toList();
        return new TechnicianState(user.getId(), active, displayName(user), technician.getVerificationStatus(),
                !requireVerified || technician.getVerificationStatus() == VerificationStatus.VALID, categories,
                technician.getProfessionalDescription(), technician.getYearsOfExperience(), coverage, slots,
                companyAdminUserId, reputation);
    }

    private static Slot slot(AvailabilitySlot slot) {
        return new Slot(slot.dayOfWeek().name(), slot.startTime().format(HOUR), slot.endTime().format(HOUR));
    }

    /** Igual que el perfil público (GC-425): nombre y la inicial del apellido. */
    static String displayName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        if (last.isEmpty()) {
            return first;
        }
        String initial = last.substring(0, last.offsetByCodePoints(0, 1)) + ".";
        return first.isEmpty() ? initial : first + " " + initial;
    }
}
