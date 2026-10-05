package com.fixia.users.repository;

import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);
}
