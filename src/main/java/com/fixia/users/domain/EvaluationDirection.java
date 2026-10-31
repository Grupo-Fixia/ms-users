package com.fixia.users.domain;

/** Quién evalúa a quién en un servicio cerrado (GC-444). Una evaluación por servicio y dirección (RN-012). */
public enum EvaluationDirection {

    /** El cliente evalúa al técnico que hizo el servicio. Es la que cuenta para la reputación del técnico (GC-446). */
    CLIENT_TO_PROVIDER,

    /** El técnico evalúa al cliente (RF-052). */
    PROVIDER_TO_CLIENT
}
