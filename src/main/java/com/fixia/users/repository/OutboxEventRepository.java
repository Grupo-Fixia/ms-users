package com.fixia.users.repository;

import com.fixia.users.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Los eventos de un agregado en el orden de su historia. */
    List<OutboxEvent> findByAggregateIdOrderByAggregateSequenceAsc(UUID aggregateId);
}
