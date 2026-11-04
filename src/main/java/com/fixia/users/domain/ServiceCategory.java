package com.fixia.users.domain;

/**
 * Categorías de servicio que puede declarar un técnico (DD V2: PLOMERIA, ELECTRICIDAD, MANTENIMIENTO,
 * CERRAJERIA, PINTURA, CARPINTERIA).
 */
public enum ServiceCategory {
    PLUMBING("Plomería"),
    ELECTRICAL("Electricidad"),
    MAINTENANCE("Mantenimiento"),
    LOCKSMITHING("Cerrajería"),
    PAINTING("Pintura"),
    CARPENTRY("Carpintería");

    private final String displayName;

    ServiceCategory(String displayName) {
        this.displayName = displayName;
    }

    /** Nombre para mostrar en el catálogo; en la API y en la base viaja el nombre de la constante. */
    public String getDisplayName() {
        return displayName;
    }
}
