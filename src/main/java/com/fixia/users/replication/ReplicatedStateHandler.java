package com.fixia.users.replication;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Guarda un estado replicado (GC-547). Una versión con secuencia menor o igual a la guardada llegó tarde o repetida y no
 * cambia nada: así las reentregas y los mensajes atrasados no deshacen un estado más nuevo.
 */
@Service
public class ReplicatedStateHandler {

    /** Qué se hizo con el estado. */
    public enum Outcome {
        APPLIED,
        STALE
    }

    private final ReplicatedStateRepository states;
    private final Clock clock;

    public ReplicatedStateHandler(ReplicatedStateRepository states, Clock clock) {
        this.states = states;
        this.clock = clock;
    }

    @Transactional
    public Outcome apply(StateEvent event) {
        StateKey key = new StateKey(event.stateType(), event.key());
        Optional<ReplicatedState> current = states.findById(key);
        String data = event.data().toString();
        if (current.isEmpty()) {
            states.save(new ReplicatedState(key, event.sequence(), event.removed(), data, clock.instant()));
            return Outcome.APPLIED;
        }
        if (current.get().getSequence() >= event.sequence()) {
            return Outcome.STALE;
        }
        current.get().replace(event.sequence(), event.removed(), data, clock.instant());
        return Outcome.APPLIED;
    }
}
