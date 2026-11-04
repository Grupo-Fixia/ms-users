package com.fixia.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Empresa prestadora (GC-466). Su identidad sale de la cuenta del administrador ({@code adminUserId}, un
 * {@link User} con rol {@link Role#COMPANY}): la empresa nunca se resuelve por un identificador que mande el cliente.
 */
@Entity
@Table(name = "companies")
public class Company {

    public static final int NAME_MAX_LENGTH = 150;
    public static final int NIT_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_user_id", nullable = false, unique = true)
    private UUID adminUserId;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    /** Opcional: el MVP no valida ni exige el NIT. */
    @Column(length = NIT_MAX_LENGTH)
    private String nit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Company() {
    }

    public Company(UUID adminUserId, String name, String nit, Instant now) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la empresa es obligatorio");
        }
        this.adminUserId = adminUserId;
        this.name = name.strip();
        this.nit = nit == null || nit.isBlank() ? null : nit.strip();
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdminUserId() {
        return adminUserId;
    }

    public String getName() {
        return name;
    }

    public String getNit() {
        return nit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
