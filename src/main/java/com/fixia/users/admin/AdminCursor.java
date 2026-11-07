package com.fixia.users.admin;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * Cursor opaco de las consultas administrativas: la fecha y el id del último elemento de la página, en base64url. El orden
 * es por fecha descendente y, ante empate, por id descendente, así que el cursor identifica una posición exacta.
 */
public record AdminCursor(Instant at, UUID id) {

    private static final String SEPARATOR = "|";

    public String encode() {
        String raw = at.toString() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code null} si no se mandó cursor; 400 si el texto no es un cursor de esta API. */
    public static AdminCursor decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value.trim()), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator <= 0) {
                throw new InvalidAdminQueryException("El cursor no es válido");
            }
            return new AdminCursor(Instant.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidAdminQueryException("El cursor no es válido");
        }
    }
}
