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

    public static final int NAME_MAX_LENGTH = 100;
    public static final int PHONE_MAX_LENGTH = 20;

    /** Teléfono en formato internacional opcional (+57...), solo dígitos, entre 7 y 15. */
    public static final String PHONE_REGEX = "^\\+?[0-9]{7,15}$";

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

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    /**
     * Actualiza los datos personales y de contacto que el titular puede editar (GC-293): nombre, apellido y
     * teléfono. No toca la identidad ni las credenciales: el correo es el login, el documento es la identidad
     * única de la cuenta, y el rol, el estado, la contraseña y el consentimiento tienen sus propios flujos.
     * Los valores se guardan sin espacios al inicio ni al final.
     *
     * @throws IllegalArgumentException si algún dato falta o no cumple el formato; la cuenta queda sin cambios
     */
    public void updateContactInfo(String firstName, String lastName, String phone) {
        String newFirstName = requireText(firstName, "El nombre", NAME_MAX_LENGTH);
        String newLastName = requireText(lastName, "El apellido", NAME_MAX_LENGTH);
        String newPhone = requireText(phone, "El teléfono", PHONE_MAX_LENGTH);
        if (!newPhone.matches(PHONE_REGEX)) {
            throw new IllegalArgumentException("El teléfono no es válido");
        }
        // Se asigna al final: si algún dato es inválido, la cuenta no queda a medio actualizar.
        this.firstName = newFirstName;
        this.lastName = newLastName;
        this.phone = newPhone;
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " es obligatorio");
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException(label + " no puede superar " + maxLength + " caracteres");
        }
        return stripped;
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
