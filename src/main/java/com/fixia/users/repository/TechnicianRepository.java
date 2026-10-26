package com.fixia.users.repository;

import com.fixia.users.domain.Technician;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TechnicianRepository extends JpaRepository<Technician, UUID> {

    /** Trae las categorías y la disponibilidad en la misma consulta: open-in-view está desactivado. */
    @EntityGraph(attributePaths = {"categories", "availability"})
    Optional<Technician> findByUserId(UUID userId);

    /** Varios técnicos a la vez, con sus categorías, para los perfiles públicos (GC-425). */
    @EntityGraph(attributePaths = {"categories"})
    List<Technician> findByUserIdIn(Collection<UUID> userIds);

    /**
     * Para escribir: toma la fila del técnico en exclusiva hasta el fin de la transacción, de modo que dos
     * actualizaciones simultáneas del mismo perfil se aplican una tras otra y la segunda ya ve las categorías
     * que dejó la primera. Las categorías y la disponibilidad se cargan después, dentro de la misma transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Technician t where t.userId = :userId")
    Optional<Technician> findByUserIdForUpdate(@Param("userId") UUID userId);
}
