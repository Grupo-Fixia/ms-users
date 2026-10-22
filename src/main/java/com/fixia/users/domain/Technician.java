package com.fixia.users.domain;

import com.fixia.users.exception.InvalidTechnicianDataException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Especialización 1:1 de {@link User} para el rol PROFESSIONAL (tabla TECNICO del DD V2).
 * La reputación se agrega con la tarea que la use.
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

    /** Null mientras el técnico no declare su zona de atención. */
    @Embedded
    private CoverageZone coverageZone;

    @OneToMany(mappedBy = "technician", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TechnicianAvailability> availability = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Bloqueo optimista: una escritura hecha sobre datos ya modificados por otra se rechaza en vez de pisarla. */
    @Version
    @Column(nullable = false)
    private long version;

    protected Technician() {
    }

    public Technician(UUID userId, Instant now) {
        this.userId = userId;
        this.verificationStatus = VerificationStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Reemplaza la información profesional y las categorías. */
    public void updateProfile(String professionalDescription, short yearsOfExperience,
                              Set<ServiceCategory> newCategories, Instant now) {
        this.professionalDescription = professionalDescription;
        this.yearsOfExperience = yearsOfExperience;
        replaceCategories(newCategories, now);
    }

    /**
     * Reemplaza las especialidades y servicios ofrecidos (RF-010) sin tocar el resto del perfil; el conjunto
     * puede quedar vacío (RN-019). Las categorías se ajustan por diferencia (no se borran y se vuelven a
     * crear) porque Hibernate ejecuta los INSERT antes que los DELETE y la unicidad (technician_id, category)
     * fallaría al conservar una categoría.
     */
    public void replaceCategories(Set<ServiceCategory> newCategories, Instant now) {
        categories.removeIf(existing -> !newCategories.contains(existing.getCategory()));
        Set<ServiceCategory> current = getCategories();
        newCategories.stream()
                .filter(category -> !current.contains(category))
                .forEach(category -> categories.add(new TechnicianCategory(this, category)));
        this.updatedAt = now;
    }

    /** Declara o reemplaza la zona de atención (RF-011); con null el técnico la retira. Ver {@link CoverageZone}. */
    public void updateCoverageZone(CoverageZone newCoverageZone, Instant now) {
        this.coverageZone = newCoverageZone;
        this.updatedAt = now;
    }

    /**
     * Reemplaza la disponibilidad semanal (RF-011); el conjunto puede quedar vacío. Las franjas no pueden
     * cruzarse entre sí. Igual que las categorías, se ajustan por diferencia para no chocar con la unicidad de
     * la tabla cuando una franja se conserva.
     */
    public void replaceAvailability(Set<AvailabilitySlot> newSlots, Instant now) {
        List<AvailabilitySlot> ordered = newSlots.stream().sorted().toList();
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i - 1).overlaps(ordered.get(i))) {
                throw new InvalidTechnicianDataException("Las franjas de disponibilidad no pueden cruzarse");
            }
        }
        availability.removeIf(existing -> !newSlots.contains(existing.getSlot()));
        List<AvailabilitySlot> current = getAvailability();
        ordered.stream()
                .filter(slot -> !current.contains(slot))
                .forEach(slot -> availability.add(new TechnicianAvailability(this, slot)));
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

    public CoverageZone getCoverageZone() {
        return coverageZone;
    }

    /** Franjas ordenadas por día y hora de inicio. */
    public List<AvailabilitySlot> getAvailability() {
        return availability.stream().map(TechnicianAvailability::getSlot).sorted().toList();
    }

    /**
     * Regla de compatibilidad por horario (para GC-337): si el momento esperado de una solicitud cae dentro de
     * alguna franja del técnico. Sin franjas declaradas nunca está disponible.
     */
    public boolean isAvailableAt(Instant instant) {
        return availability.stream().anyMatch(slot -> slot.getSlot().includes(instant));
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
