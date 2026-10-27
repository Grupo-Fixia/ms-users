package com.fixia.users.repository;

import com.fixia.users.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    /** La empresa de la que la cuenta indicada es administradora; vacía si la cuenta no administra ninguna. */
    Optional<Company> findByAdminUserId(UUID adminUserId);
}
