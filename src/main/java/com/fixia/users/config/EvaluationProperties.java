package com.fixia.users.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Reglas configurables de las evaluaciones (GC-444, RF-053).
 *
 * @param window plazo para evaluar desde el cierre del servicio: 2 días por defecto (decisión de GC-443); se cambia
 *               con {@code FIXIA_EVALUATIONS_WINDOW} (por ejemplo {@code P7D})
 */
@ConfigurationProperties(prefix = "fixia.evaluations")
public record EvaluationProperties(@DefaultValue("P2D") Duration window) {
}
