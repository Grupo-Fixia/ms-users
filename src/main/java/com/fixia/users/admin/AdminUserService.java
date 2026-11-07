package com.fixia.users.admin;

import com.fixia.users.domain.Company;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.domain.UserStatus;
import com.fixia.users.repository.CompanyRepository;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Consultas del ADMIN sobre usuarios (GC-535, contrato {@code docs/contracts/admin.md} §3.1 y §3.2). Solo lee; cada
 * consulta queda registrada con {@link AdminAuditService}, incluso la que no encuentra al usuario.
 */
@Service
public class AdminUserService {

    static final int MIN_QUERY = 2;
    static final int MAX_QUERY = 100;
    private static final int VISIBLE_DOCUMENT_DIGITS = 4;

    private final UserRepository userRepository;
    private final TechnicianRepository technicianRepository;
    private final CompanyRepository companyRepository;
    private final AdminAuditService audit;

    public AdminUserService(UserRepository userRepository, TechnicianRepository technicianRepository,
                            CompanyRepository companyRepository, AdminAuditService audit) {
        this.userRepository = userRepository;
        this.technicianRepository = technicianRepository;
        this.companyRepository = companyRepository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdminResponses.UserPage list(UUID adminId, String roleParam, String statusParam, String q,
                                        Integer limitParam, String cursorParam) {
        Role role = AdminQueries.parseEnum(Role.class, roleParam, "role");
        UserStatus status = AdminQueries.parseEnum(UserStatus.class, statusParam, "status");
        String text = searchText(q);
        int limit = AdminQueries.limit(limitParam);
        AdminCursor cursor = AdminCursor.decode(cursorParam);

        Specification<User> spec = Specification.where(AdminQueries.<User>after(cursor, "createdAt"));
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (text != null) {
            String pattern = "%" + escapeLike(text) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), pattern, '\\'),
                    cb.like(cb.lower(root.get("firstName")), pattern, '\\'),
                    cb.like(cb.lower(root.get("lastName")), pattern, '\\')));
        }

        List<User> rows = new ArrayList<>(
                userRepository.findAll(spec, AdminQueries.firstRows(limit, "createdAt")).getContent());
        String next = AdminQueries.nextCursor(rows, limit, user -> new AdminCursor(user.getCreatedAt(), user.getId()));

        Map<String, Object> filters = new LinkedHashMap<>();
        putIfPresent(filters, "role", role);
        putIfPresent(filters, "status", status);
        putIfPresent(filters, "q", text);
        filters.put("limit", limit);
        audit.record(adminId, AdminAction.USER_LIST, null, filters, AdminAuditOutcome.OK);

        return new AdminResponses.UserPage(rows.stream().map(AdminUserService::summary).toList(), next);
    }

    @Transactional(readOnly = true)
    public AdminResponses.UserDetail detail(UUID adminId, UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            audit.record(adminId, AdminAction.USER_VIEW, userId, null, AdminAuditOutcome.NOT_FOUND);
            throw new AdminUserNotFoundException();
        }
        var verification = user.getRole() == Role.PROFESSIONAL
                ? technicianRepository.findByUserId(userId).map(Technician::getVerificationStatus).orElse(null)
                : null;
        String companyName = user.getRole() == Role.COMPANY
                ? companyRepository.findByAdminUserId(userId).map(Company::getName).orElse(null)
                : null;
        audit.record(adminId, AdminAction.USER_VIEW, userId, null, AdminAuditOutcome.OK);
        return new AdminResponses.UserDetail(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRole(), user.getStatus(), user.getCreatedAt(), user.getPhone(), user.getDocumentType(),
                mask(user.getDocumentNumber()), verification, companyName);
    }

    /** Deja visibles solo los 4 últimos caracteres; si el documento es tan corto, lo oculta entero (contrato P3). */
    static String mask(String document) {
        if (document == null || document.isEmpty()) {
            return document;
        }
        if (document.length() <= VISIBLE_DOCUMENT_DIGITS) {
            return "*".repeat(document.length());
        }
        int hidden = document.length() - VISIBLE_DOCUMENT_DIGITS;
        return "*".repeat(hidden) + document.substring(hidden);
    }

    private static AdminResponses.UserSummary summary(User user) {
        return new AdminResponses.UserSummary(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRole(), user.getStatus(), user.getCreatedAt());
    }

    private static String searchText(String q) {
        if (q == null) {
            return null;
        }
        String text = q.trim();
        if (text.length() < MIN_QUERY || text.length() > MAX_QUERY) {
            throw new InvalidAdminQueryException(
                    "La búsqueda debe tener entre " + MIN_QUERY + " y " + MAX_QUERY + " caracteres");
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
