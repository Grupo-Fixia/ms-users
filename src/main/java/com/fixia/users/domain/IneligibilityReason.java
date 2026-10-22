package com.fixia.users.domain;

/**
 * Motivo por el que un técnico no es elegible para una solicitud de servicio. Los códigos son estables: ms-services
 * los devuelve al frontend para explicarle al técnico qué le falta.
 */
public enum IneligibilityReason {

    /** No hay un técnico activo con ese usuario (no existe, no es técnico o la cuenta está deshabilitada). */
    TECHNICIAN_NOT_FOUND,

    /** La categoría de la solicitud no está entre las especialidades del técnico. */
    CATEGORY,

    /** El técnico no declaró zona de atención o la ubicación de la solicitud queda fuera de ella. */
    ZONE,

    /** El momento esperado de la solicitud no cae en ninguna franja de disponibilidad del técnico. */
    AVAILABILITY,

    /** El técnico no tiene la verificación vigente (solo se exige si está activado, RN-001). */
    VERIFICATION
}
