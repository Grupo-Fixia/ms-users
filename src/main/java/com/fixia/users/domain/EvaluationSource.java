package com.fixia.users.domain;

/**
 * Origen de una evaluación (RF-055): distingue las de servicios confirmados de las de casos resueltos por la
 * plataforma. En el Sprint 5 solo existe la confirmación del cliente; la resolución administrativa llegará con las
 * disputas.
 */
public enum EvaluationSource {

    /** El cliente confirmó el cierre del servicio (GC-441). */
    CONFIRMED_CLOSURE
}
