package com.fixia.users.dto;

import java.math.BigDecimal;

/**
 * Reputación pública de una persona evaluada (GC-446): promedio de sus evaluaciones redondeado a un decimal (mitad
 * hacia arriba) y cuántas son. Se calcula al consultar a partir de las evaluaciones originales de
 * {@code service_evaluations} (opción A del contrato GC-443, §7): no hay una copia que se desincronice. Sin
 * evaluaciones no hay reputación ({@code null} en las respuestas), nunca estrellas inventadas (RF-054).
 */
public record Reputation(BigDecimal average, long count) {
}
