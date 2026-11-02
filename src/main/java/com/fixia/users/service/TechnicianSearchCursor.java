package com.fixia.users.service;

import com.fixia.users.exception.InvalidSearchCursorException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Posición de la búsqueda de técnicos: el {@code userId} del último técnico revisado. El cliente la trata como un
 * valor opaco; codificarla permite cambiar su forma sin avisar. Un cursor que este servicio no emitió da 400.
 */
final class TechnicianSearchCursor {

    /**
     * Posición anterior a cualquier técnico, para empezar una búsqueda. Evita un parámetro nulo en la consulta (con
     * PostgreSQL, un {@code UUID} nulo en {@code :after is null} no tiene tipo); un {@code users.id} generado nunca es
     * el UUID nulo.
     */
    static final UUID START = new UUID(0L, 0L);

    private TechnicianSearchCursor() {
    }

    static String encode(UUID lastUserId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(lastUserId.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** @return la posición del cursor, o {@link #START} si no hay cursor */
    static UUID decode(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return START;
        }
        try {
            String text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            UUID position = UUID.fromString(text);
            if (!position.toString().equals(text)) {
                throw new InvalidSearchCursorException();
            }
            return position;
        } catch (InvalidSearchCursorException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new InvalidSearchCursorException();
        }
    }
}
