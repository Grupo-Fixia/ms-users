package com.fixia.users.domain;

/** Estado de envío de un evento del Outbox (GC-444). Lo cambia solo el relay (GC-434); el hecho no cambia. */
public enum OutboxStatus {

    /** Esperando publicación, o reintento programado tras un fallo. */
    PENDING,

    /** Kafka confirmó la publicación. */
    PUBLISHED,

    /** Agotó sus reintentos. */
    FAILED
}
