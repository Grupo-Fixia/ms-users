package com.fixia.users.admin;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/** Reglas comunes de las listas administrativas: límite, orden y paginación por cursor (contrato §3.1). */
final class AdminQueries {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private AdminQueries() {
    }

    static int limit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        if (requested < 1 || requested > MAX_LIMIT) {
            throw new InvalidAdminQueryException("El límite debe estar entre 1 y " + MAX_LIMIT);
        }
        return requested;
    }

    /** Una fila más que el límite, para saber si hay otra página sin contar. */
    static Pageable firstRows(int limit, String dateField) {
        return PageRequest.of(0, limit + 1, Sort.by(Sort.Order.desc(dateField), Sort.Order.desc("id")));
    }

    /** Fecha descendente y, ante empate, id descendente: lo que viene después del cursor. */
    static <T> Specification<T> after(AdminCursor cursor, String dateField) {
        if (cursor == null) {
            return null;
        }
        return (root, query, cb) -> {
            Predicate older = cb.lessThan(root.<Instant>get(dateField), cursor.at());
            Predicate sameDate = cb.equal(root.<Instant>get(dateField), cursor.at());
            Predicate smallerId = cb.lessThan(root.<UUID>get("id"), cursor.id());
            return cb.or(older, cb.and(sameDate, smallerId));
        };
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidAdminQueryException("El valor de " + field + " no es válido");
        }
    }

    /** Si hay una fila de más, la quita y devuelve el cursor del último elemento que sí va en la página. */
    static <T> String nextCursor(java.util.List<T> rows, int limit, Function<T, AdminCursor> cursorOf) {
        if (rows.size() <= limit) {
            return null;
        }
        rows.subList(limit, rows.size()).clear();
        return cursorOf.apply(rows.get(limit - 1)).encode();
    }
}
