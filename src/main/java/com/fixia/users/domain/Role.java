package com.fixia.users.domain;

public enum Role {
    CLIENT,
    PROFESSIONAL,
    /** Administrador de una empresa prestadora (GC-466). Distinto de ADMIN, reservado para la plataforma. */
    COMPANY,
    ADMIN
}
