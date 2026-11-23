package com.fixia.users.replication;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Si guardar un estado replicado falla (por ejemplo, con la base caída), se espera y se vuelve al mismo mensaje sin
 * límite de intentos y sin confirmar el offset: saltarlo dejaría la copia local desactualizada (GC-547).
 */
@Configuration
@ConditionalOnProperty(prefix = "fixia.replication.consumer", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class StateConsumerConfig {

    @Bean
    public DefaultErrorHandler replicationConsumerErrorHandler(
            @Value("${fixia.replication.consumer.retry-delay-ms:5000}") long retryDelayMs) {
        return new DefaultErrorHandler(new FixedBackOff(retryDelayMs, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
