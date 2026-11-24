package com.fixia.users.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propiedades de las evaluaciones (GC-444). Desde GC-547 ms-users ya no tiene clientes HTTP hacia ms-services ni
 * ms-payments: lo que necesita de ellos le llega por estados replicados.
 */
@Configuration
@EnableConfigurationProperties(EvaluationProperties.class)
public class ServicesClientConfig {
}
