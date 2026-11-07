package com.fixia.users.repository;

import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/** También {@link JpaSpecificationExecutor}: las consultas del ADMIN (GC-535) combinan filtros opcionales. */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    /** Cuenta del rol indicado; vacía si el id no existe o es de otro rol (por ejemplo, un técnico). */
    Optional<User> findByIdAndRole(UUID id, Role role);

    boolean existsByEmail(String email);

    boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);
}
