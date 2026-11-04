package com.fixia.users.repository;

import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.domain.Technician;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.time.LocalTime;
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
     * Un lote de la búsqueda de técnicos por categoría (matching): los que ofrecen {@code category}, tienen la cuenta
     * activa y son de rol PROFESSIONAL, con {@code userId} posterior a {@code after}, en orden de {@code userId}. No
     * trae colecciones (evita paginar en memoria); las categorías se cargan después con {@link #findByUserIdIn}. La
     * ciudad no se filtra aquí: su comparación ignora tildes y se hace en {@code CoverageZone#matchesCity}.
     *
     * <p>Dos filtros opcionales (GC-474), que se encienden con un booleano y no con un parámetro nulo, porque en
     * PostgreSQL un parámetro nulo sin tipo falla. Con {@code filterByTime} solo pasan los técnicos con una franja que
     * incluya {@code day} y {@code time} (hora de Colombia; inicio incluido, fin excluido, como
     * {@code AvailabilitySlot#includes}); se hace aquí y no con {@code Technician#isAvailableAt} para no cargar las
     * franjas de cada técnico revisado. Con {@code requireVerified} solo pasan los de verificación vigente.
     */
    @Query("""
            select t from Technician t
            where t.userId > :after
              and exists (select 1 from TechnicianCategory c
                          where c.technician = t and c.category = :category)
              and exists (select 1 from User u
                          where u.id = t.userId
                            and u.status = com.fixia.users.domain.UserStatus.ACTIVE
                            and u.role = com.fixia.users.domain.Role.PROFESSIONAL)
              and (:filterByTime = false
                   or exists (select 1 from TechnicianAvailability a
                              where a.technician = t and a.dayOfWeek = :day
                                and a.startTime <= :time and a.endTime > :time))
              and (:requireVerified = false
                   or t.verificationStatus = com.fixia.users.domain.VerificationStatus.VALID)
            order by t.userId
            """)
    List<Technician> findSearchBatch(@Param("after") UUID after, @Param("category") ServiceCategory category,
                                     @Param("filterByTime") boolean filterByTime, @Param("day") DayOfWeek day,
                                     @Param("time") LocalTime time, @Param("requireVerified") boolean requireVerified,
                                     Pageable pageable);

    /**
     * Para escribir: toma la fila del técnico en exclusiva hasta el fin de la transacción, de modo que dos
     * actualizaciones simultáneas del mismo perfil se aplican una tras otra y la segunda ya ve las categorías
     * que dejó la primera. Las categorías y la disponibilidad se cargan después, dentro de la misma transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Technician t where t.userId = :userId")
    Optional<Technician> findByUserIdForUpdate(@Param("userId") UUID userId);
}
