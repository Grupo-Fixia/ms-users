package com.fixia.users.replication;

import com.fixia.users.domain.Company;
import com.fixia.users.domain.CompanyTechnician;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.dto.Reputation;
import com.fixia.users.repository.CompanyRepository;
import com.fixia.users.repository.CompanyTechnicianRepository;
import com.fixia.users.repository.UserRepository;
import com.fixia.users.service.ReputationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Estado de cada empresa para ms-services (GC-547): si está activa, su nombre comercial y su reputación con sus técnicos
 * actuales (GC-551). Reemplaza la ruta interna de perfiles de empresa; junto con el estado de los técnicos
 * ({@code companyAdminUserId}) reemplaza también la pertenencia. La clave es la cuenta administradora, que es el
 * {@code providerId} con que ms-services y ms-payments conocen a la empresa. Sin NIT ni datos de contacto.
 */
@Component
public class CompanyStateSource implements StateSource {

    public static final String STATE_TYPE = "empresa.estado";
    public static final String TOPIC = "fixia.users.company-states";

    record CompanyState(UUID companyAdminUserId, boolean active, String name, Reputation reputation) {
    }

    private final UserRepository users;
    private final CompanyRepository companies;
    private final CompanyTechnicianRepository associations;
    private final ReputationService reputations;

    public CompanyStateSource(UserRepository users, CompanyRepository companies,
                              CompanyTechnicianRepository associations, ReputationService reputations) {
        this.users = users;
        this.companies = companies;
        this.associations = associations;
        this.reputations = reputations;
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
        return "CompanyState";
    }

    @Override
    public Map<UUID, Object> currentStates() {
        List<User> all = users.findAll();
        Set<UUID> activeTechnicians = all.stream()
                .filter(user -> user.getRole() == Role.PROFESSIONAL && user.getStatus() == UserStatus.ACTIVE)
                .map(User::getId)
                .collect(Collectors.toSet());
        Map<UUID, Company> companyByAdmin = companies.findAll().stream()
                .collect(Collectors.toMap(Company::getAdminUserId, Function.identity()));
        Map<UUID, List<UUID>> techniciansByCompanyId = new HashMap<>();
        for (CompanyTechnician association : associations.findAll()) {
            if (activeTechnicians.contains(association.getTechnicianUserId())) {
                techniciansByCompanyId.computeIfAbsent(association.getCompanyId(), id -> new ArrayList<>())
                        .add(association.getTechnicianUserId());
            }
        }
        Map<UUID, List<UUID>> techniciansByAdmin = new HashMap<>();
        companyByAdmin.forEach((admin, company) ->
                techniciansByAdmin.put(admin, techniciansByCompanyId.getOrDefault(company.getId(), List.of())));
        Map<UUID, Reputation> reputationByAdmin = reputations.ofCompanies(techniciansByAdmin);

        Map<UUID, Object> states = new LinkedHashMap<>();
        for (User user : all) {
            if (user.getRole() != Role.COMPANY) {
                continue;
            }
            Company company = companyByAdmin.get(user.getId());
            boolean active = user.getStatus() == UserStatus.ACTIVE && company != null;
            states.put(user.getId(), new CompanyState(user.getId(), active,
                    company == null ? null : company.getName(),
                    company == null ? null : reputationByAdmin.get(user.getId())));
        }
        return states;
    }
}
