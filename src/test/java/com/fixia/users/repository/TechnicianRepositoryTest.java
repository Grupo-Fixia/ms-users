package com.fixia.users.repository;

import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistencia del perfil profesional. Cada paso corre en su propia transacción, como lo hará el servicio,
 * para comprobar lo que realmente queda en la base y no lo que guarda en memoria el contexto de persistencia.
 */
@SpringBootTest
@ActiveProfiles("test")
class TechnicianRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // DELETE inmediato en la base; el borrado en cascada se lleva técnicos, categorías y refresh tokens.
        userRepository.deleteAllInBatch();
    }

    private UUID createTechnician() {
        Instant now = Instant.now();
        User user = userRepository.save(new User("carlos@example.com", "hash", Role.PROFESSIONAL, "Carlos", "Gómez",
                DocumentType.CC, "79800900", "3104445566", "v1.0", now));
        technicianRepository.save(new Technician(user.getId(), now));
        return user.getId();
    }

    private void updateProfile(UUID userId, String description, int years, Set<ServiceCategory> categories) {
        transactionTemplate.executeWithoutResult(status -> technicianRepository.findByUserId(userId).orElseThrow()
                .updateProfile(description, (short) years, categories, Instant.now()));
    }

    private void replaceCategories(UUID userId, Set<ServiceCategory> categories) {
        transactionTemplate.executeWithoutResult(status -> technicianRepository.findByUserId(userId).orElseThrow()
                .replaceCategories(categories, Instant.now()));
    }

    private int storedCategoryRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM technician_categories", Integer.class);
    }

    @Test
    void unTecnicoNuevoNoTieneInformacionProfesional() {
        UUID userId = createTechnician();

        Technician technician = technicianRepository.findByUserId(userId).orElseThrow();

        assertThat(technician.getProfessionalDescription()).isNull();
        assertThat(technician.getYearsOfExperience()).isNull();
        assertThat(technician.getCategories()).isEmpty();
    }

    @Test
    void guardaLaInformacionProfesionalYSusCategorias() {
        UUID userId = createTechnician();
        Instant createdAt = technicianRepository.findByUserId(userId).orElseThrow().getUpdatedAt();

        updateProfile(userId, "Plomero residencial", 8, Set.of(ServiceCategory.PLUMBING, ServiceCategory.MAINTENANCE));

        Technician stored = technicianRepository.findByUserId(userId).orElseThrow();
        assertThat(stored.getProfessionalDescription()).isEqualTo("Plomero residencial");
        assertThat(stored.getYearsOfExperience()).isEqualTo((short) 8);
        assertThat(stored.getCategories()).containsExactlyInAnyOrder(ServiceCategory.PLUMBING, ServiceCategory.MAINTENANCE);
        assertThat(stored.getUpdatedAt()).isAfterOrEqualTo(createdAt);
        assertThat(storedCategoryRows()).isEqualTo(2);
    }

    @Test
    void alActualizarConservaQuitaYAgregaCategoriasSinDuplicarFilas() {
        UUID userId = createTechnician();
        updateProfile(userId, "Plomero", 8, Set.of(ServiceCategory.PLUMBING, ServiceCategory.MAINTENANCE));

        updateProfile(userId, "Plomero y electricista", 9, Set.of(ServiceCategory.PLUMBING, ServiceCategory.ELECTRICAL));

        Technician stored = technicianRepository.findByUserId(userId).orElseThrow();
        assertThat(stored.getProfessionalDescription()).isEqualTo("Plomero y electricista");
        assertThat(stored.getYearsOfExperience()).isEqualTo((short) 9);
        assertThat(stored.getCategories()).containsExactlyInAnyOrder(ServiceCategory.PLUMBING, ServiceCategory.ELECTRICAL);
        assertThat(storedCategoryRows()).isEqualTo(2);
    }

    @Test
    void laBaseImpideRepetirUnaCategoriaEnElMismoTecnico() {
        UUID userId = createTechnician();
        UUID technicianId = technicianRepository.findByUserId(userId).orElseThrow().getId();
        String insert = "INSERT INTO technician_categories (id, technician_id, category) VALUES (?, ?, 'PLUMBING')";
        jdbcTemplate.update(insert, UUID.randomUUID(), technicianId);

        assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID(), technicianId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reemplazaLasEspecialidadesSinTocarLaInformacionProfesional() {
        UUID userId = createTechnician();
        updateProfile(userId, "Plomero residencial", 8, Set.of(ServiceCategory.PLUMBING, ServiceCategory.MAINTENANCE));

        replaceCategories(userId, Set.of(ServiceCategory.PLUMBING, ServiceCategory.CARPENTRY));

        Technician stored = technicianRepository.findByUserId(userId).orElseThrow();
        assertThat(stored.getCategories()).containsExactlyInAnyOrder(ServiceCategory.PLUMBING, ServiceCategory.CARPENTRY);
        assertThat(stored.getProfessionalDescription()).isEqualTo("Plomero residencial");
        assertThat(stored.getYearsOfExperience()).isEqualTo((short) 8);
        assertThat(storedCategoryRows()).isEqualTo(2);
    }

    @Test
    void unTecnicoSinInformacionProfesionalPuedeDeclararYRetirarEspecialidades() {
        UUID userId = createTechnician();

        replaceCategories(userId, Set.of(ServiceCategory.ELECTRICAL));
        assertThat(technicianRepository.findByUserId(userId).orElseThrow().getCategories())
                .containsExactly(ServiceCategory.ELECTRICAL);

        replaceCategories(userId, Set.of());
        Technician stored = technicianRepository.findByUserId(userId).orElseThrow();
        assertThat(stored.getCategories()).isEmpty();
        assertThat(stored.getProfessionalDescription()).isNull();
        assertThat(storedCategoryRows()).isZero();
    }

    @Test
    void laBaseAceptaTodasLasCategoriasDelCatalogo() {
        UUID userId = createTechnician();

        replaceCategories(userId, Set.of(ServiceCategory.values()));

        assertThat(storedCategoryRows()).isEqualTo(ServiceCategory.values().length);
    }

    @Test
    void laBaseRechazaUnaCategoriaFueraDelCatalogo() {
        UUID userId = createTechnician();
        UUID technicianId = technicianRepository.findByUserId(userId).orElseThrow().getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO technician_categories (id, technician_id, category) VALUES (?, ?, 'GARDENING')",
                UUID.randomUUID(), technicianId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(storedCategoryRows()).isZero();
    }

    @Test
    void cadaCambioDelPerfilAvanzaLaVersion() {
        UUID userId = createTechnician();
        long initial = technicianRepository.findByUserId(userId).orElseThrow().getVersion();

        replaceCategories(userId, Set.of(ServiceCategory.PLUMBING));

        assertThat(technicianRepository.findByUserId(userId).orElseThrow().getVersion()).isEqualTo(initial + 1);
    }

    @Test
    void unaEscrituraSobreDatosDesactualizadosSeRechazaYNoPisaLaAnterior() {
        UUID userId = createTechnician();
        Technician first = technicianRepository.findByUserId(userId).orElseThrow();
        Technician stale = technicianRepository.findByUserId(userId).orElseThrow();

        first.replaceCategories(Set.of(ServiceCategory.PLUMBING), Instant.now());
        technicianRepository.saveAndFlush(first);

        stale.replaceCategories(Set.of(ServiceCategory.PAINTING), Instant.now());
        assertThatThrownBy(() -> technicianRepository.saveAndFlush(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(technicianRepository.findByUserId(userId).orElseThrow().getCategories())
                .containsExactly(ServiceCategory.PLUMBING);
    }

    @Test
    void alBorrarElUsuarioSeBorranSuPerfilYSusCategorias() {
        UUID userId = createTechnician();
        updateProfile(userId, "Plomero", 8, Set.of(ServiceCategory.PLUMBING));

        userRepository.deleteAllInBatch();

        assertThat(technicianRepository.count()).isZero();
        assertThat(storedCategoryRows()).isZero();
    }
}
