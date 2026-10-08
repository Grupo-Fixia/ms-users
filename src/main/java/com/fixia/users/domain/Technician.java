package com.fixia.users.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Especialización 1:1 de {@link User} para el rol PROFESSIONAL (tabla TECNICO del DD V2).
 * Reputación y cobertura se agregan con las tareas que las usan.
 */
@Entity
@Table(name = "technicians")
public class Technician {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Column(name = "professional_description", length = 1000)
    private String professionalDescription;

    @Column(name = "years_of_experience")
    private Short yearsOfExperience;

    @OneToMany(mappedBy = "technician", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TechnicianCategory> categories = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Technician() {
    }

    public Technician(UUID userId, Instant now) {
        this.userId = userId;
        this.verificationStatus = VerificationStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Reemplaza la información profesional. Las categorías se ajustan por diferencia (no se borran y se
     * vuelven a crear) porque Hibernate ejecuta los INSERT antes que los DELETE y la unicidad
     * (technician_id, category) fallaría al conservar una categoría.
     */
    public void updateProfile(String professionalDescription, short yearsOfExperience,
                              Set<ServiceCategory> newCategories, Instant now) {
        this.professionalDescription = professionalDescription;
        this.yearsOfExperience = yearsOfExperience;
        categories.removeIf(existing -> !newCategories.contains(existing.getCategory()));
        Set<ServiceCategory> current = getCategories();
        newCategories.stream()
                .filter(category -> !current.contains(category))
                .forEach(category -> categories.add(new TechnicianCategory(this, category)));
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public String getProfessionalDescription() {
        return professionalDescription;
    }

    public Short getYearsOfExperience() {
        return yearsOfExperience;
    }

    public Set<ServiceCategory> getCategories() {
        Set<ServiceCategory> result = EnumSet.noneOf(ServiceCategory.class);
        categories.forEach(category -> result.add(category.getCategory()));
        return result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
