package com.fixia.users.repository;

import com.fixia.users.domain.AvailabilitySlot;
import com.fixia.users.domain.CoverageZone;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistencia de la disponibilidad y la zona de atención (RF-011). Cada paso corre en su propia transacción,
 * como lo hará el servicio, para comprobar lo que realmente queda en la base.
 */
@SpringBootTest
@ActiveProfiles("test")
class TechnicianAvailabilityRepositoryTest {

    private static final AvailabilitySlot MONDAY_MORNING = slot(DayOfWeek.MONDAY, 8, 12);
    private static final AvailabilitySlot MONDAY_AFTERNOON = slot(DayOfWeek.MONDAY, 14, 18);
    private static final AvailabilitySlot SATURDAY_MORNING = slot(DayOfWeek.SATURDAY, 9, 13);

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
        // DELETE inmediato en la base; el borrado en cascada se lleva técnicos, categorías y disponibilidad.
        userRepository.deleteAllInBatch();
    }

    private static AvailabilitySlot slot(DayOfWeek day, int startHour, int endHour) {
        return new AvailabilitySlot(day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private static CoverageZone bogota(int radiusKm) {
        return new CoverageZone("Bogotá", 4.653281, -74.083600, radiusKm);
    }

    private UUID createTechnician() {
        Instant now = Instant.now();
        User user = userRepository.save(new User("carlos@example.com", "hash", Role.PROFESSIONAL, "Carlos", "Gómez",
                DocumentType.CC, "79800900", "3104445566", "v1.0", now));
        technicianRepository.save(new Technician(user.getId(), now));
        return user.getId();
    }

    private void change(UUID userId, Consumer<Technician> action) {
        transactionTemplate.executeWithoutResult(
                status -> action.accept(technicianRepository.findByUserId(userId).orElseThrow()));
    }

    private Technician stored(UUID userId) {
        return technicianRepository.findByUserId(userId).orElseThrow();
    }

    private UUID technicianId(UUID userId) {
        return stored(userId).getId();
    }

    private int storedAvailabilityRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM technician_availability", Integer.class);
    }

    // ---------- Zona de atención ----------

    @Test
    void unTecnicoNuevoNoTieneZonaNiDisponibilidad() {
        UUID userId = createTechnician();

        assertThat(stored(userId).getCoverageZone()).isNull();
        assertThat(stored(userId).getAvailability()).isEmpty();
    }

    @Test
    void guardaYReemplazaLaZonaDeAtencion() {
        UUID userId = createTechnician();

        change(userId, technician -> technician.updateCoverageZone(bogota(15), Instant.now()));
        assertThat(stored(userId).getCoverageZone()).isEqualTo(bogota(15));

        CoverageZone medellin = new CoverageZone("Medellín", 6.244203, -75.581212, 30);
        change(userId, technician -> technician.updateCoverageZone(medellin, Instant.now()));

        CoverageZone zone = stored(userId).getCoverageZone();
        assertThat(zone.getCity()).isEqualTo("Medellín");
        assertThat(zone.getLatitude()).isEqualTo(6.24);
        assertThat(zone.getLongitude()).isEqualTo(-75.58);
        assertThat(zone.getRadiusKm()).isEqualTo((short) 30);
    }

    @Test
    void guardaUnaZonaDeSoloCiudadYLaPuedeAmpliarConUnCirculo() {
        UUID userId = createTechnician();

        change(userId, technician -> technician.updateCoverageZone(new CoverageZone("Cali"), Instant.now()));
        CoverageZone cityOnly = stored(userId).getCoverageZone();
        assertThat(cityOnly).isEqualTo(new CoverageZone("Cali"));
        assertThat(cityOnly.hasCircle()).isFalse();

        change(userId, technician -> technician.updateCoverageZone(
                new CoverageZone("Cali", 3.45, -76.53, 8), Instant.now()));
        assertThat(stored(userId).getCoverageZone().hasCircle()).isTrue();
    }

    @Test
    void elTecnicoPuedeRetirarSuZonaDeAtencion() {
        UUID userId = createTechnician();
        change(userId, technician -> technician.updateCoverageZone(bogota(15), Instant.now()));

        change(userId, technician -> technician.updateCoverageZone(null, Instant.now()));

        assertThat(stored(userId).getCoverageZone()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM technicians WHERE coverage_city IS NULL AND coverage_latitude IS NULL"
                        + " AND coverage_longitude IS NULL AND coverage_radius_km IS NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void laBaseRechazaUnaZonaIncompletaOFueraDeRango() {
        UUID id = technicianId(createTechnician());
        String update = "UPDATE technicians SET coverage_city = ?, coverage_latitude = ?, coverage_longitude = ?,"
                + " coverage_radius_km = ? WHERE id = ?";

        assertThatThrownBy(() -> jdbcTemplate.update(update, "Bogotá", 4.65, null, null, id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(update, null, 4.65, -74.08, 10, id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(update, "Bogotá", 91.0, -74.08, 10, id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(update, "Bogotá", 4.65, -181.0, 10, id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(update, "Bogotá", 4.65, -74.08, 0, id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(update, "Bogotá", 4.65, -74.08, 101, id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.update(update, "Bogotá", null, null, null, id)).isEqualTo(1);
    }

    // ---------- Disponibilidad ----------

    @Test
    void guardaLaDisponibilidadConVariasFranjasPorDiaYLaDevuelveOrdenada() {
        UUID userId = createTechnician();

        change(userId, technician -> technician.replaceAvailability(
                Set.of(SATURDAY_MORNING, MONDAY_AFTERNOON, MONDAY_MORNING), Instant.now()));

        assertThat(stored(userId).getAvailability())
                .containsExactly(MONDAY_MORNING, MONDAY_AFTERNOON, SATURDAY_MORNING);
        assertThat(storedAvailabilityRows()).isEqualTo(3);
    }

    @Test
    void alReemplazarConservaQuitaAgregaYAjustaFranjasSinDuplicarFilas() {
        UUID userId = createTechnician();
        change(userId, technician -> technician.replaceAvailability(
                Set.of(MONDAY_MORNING, MONDAY_AFTERNOON, SATURDAY_MORNING), Instant.now()));

        // Conserva la mañana del lunes, alarga la tarde (misma hora de inicio), quita el sábado y agrega el martes.
        AvailabilitySlot longerAfternoon = slot(DayOfWeek.MONDAY, 14, 20);
        AvailabilitySlot tuesday = slot(DayOfWeek.TUESDAY, 8, 12);
        change(userId, technician -> technician.replaceAvailability(
                Set.of(MONDAY_MORNING, longerAfternoon, tuesday), Instant.now()));

        assertThat(stored(userId).getAvailability()).containsExactly(MONDAY_MORNING, longerAfternoon, tuesday);
        assertThat(storedAvailabilityRows()).isEqualTo(3);
    }

    @Test
    void elTecnicoPuedeQuedarSinDisponibilidad() {
        UUID userId = createTechnician();
        change(userId, technician -> technician.replaceAvailability(Set.of(MONDAY_MORNING), Instant.now()));

        change(userId, technician -> technician.replaceAvailability(Set.of(), Instant.now()));

        assertThat(stored(userId).getAvailability()).isEmpty();
        assertThat(storedAvailabilityRows()).isZero();
    }

    @Test
    void rechazaFranjasQueSeCruzanYNoCambiaLoGuardado() {
        UUID userId = createTechnician();
        change(userId, technician -> technician.replaceAvailability(Set.of(MONDAY_MORNING), Instant.now()));

        assertThatThrownBy(() -> change(userId, technician -> technician.replaceAvailability(
                Set.of(MONDAY_MORNING, slot(DayOfWeek.MONDAY, 11, 15)), Instant.now())))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(stored(userId).getAvailability()).containsExactly(MONDAY_MORNING);
    }

    @Test
    void sabeSiElTecnicoEstaDisponibleEnElMomentoEsperadoDeUnaSolicitud() {
        UUID userId = createTechnician();
        assertThat(stored(userId).isAvailableAt(Instant.parse("2026-10-19T14:00:00Z"))).isFalse();

        change(userId, technician -> technician.replaceAvailability(
                Set.of(MONDAY_MORNING, SATURDAY_MORNING), Instant.now()));

        Technician technician = stored(userId);
        // 2026-10-19 es lunes: 14:00 UTC son las 09:00 en Bogotá; 18:00 UTC son las 13:00.
        assertThat(technician.isAvailableAt(Instant.parse("2026-10-19T14:00:00Z"))).isTrue();
        assertThat(technician.isAvailableAt(Instant.parse("2026-10-19T18:00:00Z"))).isFalse();
        // 2026-10-24 es sábado: 15:00 UTC son las 10:00 en Bogotá.
        assertThat(technician.isAvailableAt(Instant.parse("2026-10-24T15:00:00Z"))).isTrue();
    }

    @Test
    void aceptaFranjasSeguidasElMismoDia() {
        UUID userId = createTechnician();
        AvailabilitySlot noon = slot(DayOfWeek.MONDAY, 12, 14);

        change(userId, technician -> technician.replaceAvailability(Set.of(MONDAY_MORNING, noon), Instant.now()));

        assertThat(stored(userId).getAvailability()).containsExactly(MONDAY_MORNING, noon);
    }

    @Test
    void laBaseRechazaFranjasInvalidasORepetidas() {
        UUID id = technicianId(createTechnician());
        String insert = "INSERT INTO technician_availability (id, technician_id, day_of_week, start_time, end_time)"
                + " VALUES (?, ?, ?, ?, ?)";
        LocalTime eight = LocalTime.of(8, 0);
        LocalTime noon = LocalTime.of(12, 0);
        jdbcTemplate.update(insert, UUID.randomUUID(), id, "MONDAY", eight, noon);

        assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID(), id, "MONDAY", eight, noon))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID(), id, "LUNES", eight, noon))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID(), id, "TUESDAY", noon, eight))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(storedAvailabilityRows()).isEqualTo(1);
    }

    // ---------- Relación con el resto del perfil ----------

    @Test
    void laZonaYLaDisponibilidadNoTocanElRestoDelPerfilYAvanzanLaVersion() {
        UUID userId = createTechnician();
        change(userId, technician -> technician.updateProfile("Plomero residencial", (short) 8,
                Set.of(ServiceCategory.PLUMBING), Instant.now()));
        long version = stored(userId).getVersion();

        change(userId, technician -> technician.updateCoverageZone(bogota(15), Instant.now()));
        change(userId, technician -> technician.replaceAvailability(Set.of(MONDAY_MORNING), Instant.now()));

        Technician technician = stored(userId);
        assertThat(technician.getProfessionalDescription()).isEqualTo("Plomero residencial");
        assertThat(technician.getYearsOfExperience()).isEqualTo((short) 8);
        assertThat(technician.getCategories()).containsExactly(ServiceCategory.PLUMBING);
        assertThat(technician.getVersion()).isEqualTo(version + 2);
    }

    @Test
    void conVariasCategoriasYVariasFranjasNoSeMultiplicanLosDatosAlLeer() {
        UUID userId = createTechnician();
        change(userId, technician -> technician.replaceCategories(
                Set.of(ServiceCategory.PLUMBING, ServiceCategory.ELECTRICAL, ServiceCategory.PAINTING), Instant.now()));
        change(userId, technician -> technician.replaceAvailability(
                Set.of(MONDAY_MORNING, MONDAY_AFTERNOON), Instant.now()));

        Technician technician = stored(userId);

        assertThat(technician.getCategories()).hasSize(3);
        assertThat(technician.getAvailability()).containsExactly(MONDAY_MORNING, MONDAY_AFTERNOON);
    }

    @Test
    void alBorrarElUsuarioSeBorraSuDisponibilidad() {
        UUID userId = createTechnician();
        change(userId, technician -> technician.replaceAvailability(Set.of(MONDAY_MORNING), Instant.now()));

        userRepository.deleteAllInBatch();

        assertThat(storedAvailabilityRows()).isZero();
    }
}
