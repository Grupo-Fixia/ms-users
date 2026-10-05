package com.fixia.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false, length = 30)
    private String documentNumber;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "consent_policy_version", nullable = false, length = 20)
    private String consentPolicyVersion;

    @Column(name = "consent_accepted_at", nullable = false)
    private Instant consentAcceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String email, String passwordHash, Role role, String firstName, String lastName,
                DocumentType documentType, String documentNumber, String phone,
                String consentPolicyVersion, Instant now) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = UserStatus.ACTIVE;
        this.firstName = firstName;
        this.lastName = lastName;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.phone = phone;
        this.consentPolicyVersion = consentPolicyVersion;
        this.consentAcceptedAt = now;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getPhone() {
        return phone;
    }

    public String getConsentPolicyVersion() {
        return consentPolicyVersion;
    }

    public Instant getConsentAcceptedAt() {
        return consentAcceptedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
