package com.fixia.users.repository;

import java.util.UUID;

/** Suma y número de las evaluaciones que recibió una persona en una dirección (GC-446). */
public interface ReputationTotals {

    UUID getEvaluatedId();

    Long getRatingSum();

    Long getRatingCount();
}
