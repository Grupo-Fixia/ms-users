package com.fixia.users.repository;

import com.fixia.users.domain.Technician;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TechnicianRepository extends JpaRepository<Technician, UUID> {

    /** Trae las categorías en la misma consulta: open-in-view está desactivado. */
    @EntityGraph(attributePaths = "categories")
    Optional<Technician> findByUserId(UUID userId);
}
