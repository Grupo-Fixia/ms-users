package com.fixia.users.repository;

import com.fixia.users.domain.Technician;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TechnicianRepository extends JpaRepository<Technician, UUID> {

    Optional<Technician> findByUserId(UUID userId);
}
