package com.fixia.users.service;

import com.fixia.users.domain.CoverageZone;
import com.fixia.users.domain.DocumentType;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.ServiceCategory;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.dto.TechnicianSearchRequest;
import com.fixia.users.dto.TechnicianSearchResponse;
import com.fixia.users.exception.InvalidSearchCursorException;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Presupuesto de revisión y cursor de la búsqueda de técnicos (GC-900), con el repositorio simulado: probar 500
 * técnicos reales en una base sería lento y no añade nada a la regla.
 */
class TechnicianSearchServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    private TechnicianRepository technicianRepository;
    private UserRepository userRepository;
    private TechnicianSearchService service;

    @BeforeEach
    void setUp() {
        technicianRepository = mock(TechnicianRepository.class);
        userRepository = mock(UserRepository.class);
        service = new TechnicianSearchService(technicianRepository, userRepository, false);
    }

    // ---------- Utilidades ----------

    /** Ids crecientes y fáciles de leer: el técnico {@code n} tiene el UUID {@code 0-n}. */
    private static UUID id(int n) {
        return new UUID(0L, n);
    }

    private static Technician technician(int n, String city) {
        Technician technician = new Technician(id(n), NOW);
        technician.replaceCategories(Set.of(ServiceCategory.PLUMBING), NOW);
        technician.updateCoverageZone(new CoverageZone(city), NOW);
        return technician;
    }

    private static List<Technician> technicians(int from, int count, String city) {
        List<Technician> result = new ArrayList<>();
        for (int n = from; n < from + count; n++) {
            result.add(technician(n, city));
        }
        return result;
    }

    private static User user(int n) {
        User user = new User("t" + n + "@example.com", "hash", Role.PROFESSIONAL, "Carlos", "Gómez", DocumentType.CC,
                String.valueOf(1000 + n), "3104445566", "v1.0", NOW);
        ReflectionTestUtils.setField(user, "id", id(n));
        return user;
    }

    /** Lo que las consultas de categorías y de nombres devolverían para los técnicos encontrados. */
    private void stubDetails(List<Technician> found) {
        when(technicianRepository.findByUserIdIn(any())).thenReturn(found);
        when(userRepository.findAllById(any())).thenReturn(
                found.stream().map(technician -> user((int) technician.getUserId().getLeastSignificantBits()))
                        .toList());
    }

    private static TechnicianSearchRequest request(int limit, String cursor) {
        return new TechnicianSearchRequest(ServiceCategory.PLUMBING, "Bogotá", limit, cursor, null);
    }

    // ---------- Reglas ----------

    @Test
    void sinMasTecnicosPorRevisarElCursorEsNulo() {
        List<Technician> batch = List.of(technician(1, "Bogotá"), technician(2, "Medellín"), technician(3, "bogota"));
        when(technicianRepository.findSearchBatch(any(), eq(ServiceCategory.PLUMBING), anyBoolean(), any(), any(), anyBoolean(), any())).thenReturn(batch);
        stubDetails(List.of(batch.get(0), batch.get(2)));

        TechnicianSearchResponse response = service.search(request(20, null));

        assertThat(response.items()).extracting(TechnicianSearchResponse.Item::userId).containsExactly(id(1), id(3));
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void empiezaDesdeElPrincipioSinCursorYDesdeLaPosicionConCursor() {
        when(technicianRepository.findSearchBatch(any(), any(), anyBoolean(), any(), any(), anyBoolean(), any())).thenReturn(List.of());

        service.search(request(20, null));
        service.search(request(20, TechnicianSearchCursor.encode(id(7))));

        ArgumentCaptor<UUID> after = ArgumentCaptor.forClass(UUID.class);
        verify(technicianRepository, times(2)).findSearchBatch(after.capture(), any(), anyBoolean(), any(), any(), anyBoolean(), any(Pageable.class));
        assertThat(after.getAllValues()).containsExactly(TechnicianSearchCursor.START, id(7));
    }

    @Test
    void siSeLlenaLaPaginaALaMitadDeUnLoteElCursorApuntaAlUltimoDevuelto() {
        List<Technician> batch = List.of(technician(1, "Bogotá"), technician(2, "Bogotá"), technician(3, "Bogotá"));
        when(technicianRepository.findSearchBatch(any(), any(), anyBoolean(), any(), any(), anyBoolean(), any())).thenReturn(batch);
        stubDetails(batch.subList(0, 2));

        TechnicianSearchResponse response = service.search(request(2, null));

        assertThat(response.items()).extracting(TechnicianSearchResponse.Item::userId).containsExactly(id(1), id(2));
        assertThat(response.nextCursor()).isEqualTo(TechnicianSearchCursor.encode(id(2)));
    }

    @Test
    void siSeLlenaLaPaginaEnElUltimoTecnicoDeUnLoteCortoNoQuedaNadaPorRevisar() {
        List<Technician> batch = List.of(technician(1, "Bogotá"), technician(2, "Bogotá"));
        when(technicianRepository.findSearchBatch(any(), any(), anyBoolean(), any(), any(), anyBoolean(), any())).thenReturn(batch);
        stubDetails(batch);

        TechnicianSearchResponse response = service.search(request(2, null));

        assertThat(response.items()).hasSize(2);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void siLoteLlenoSeAgotaElPresupuestoDevuelveLoEncontradoYUnCursor() {
        List<Technician> first = technicians(1, TechnicianSearchService.BATCH_SIZE, "Medellín");
        List<Technician> second = technicians(101, TechnicianSearchService.BATCH_SIZE, "Medellín");
        List<Technician> third = technicians(201, TechnicianSearchService.BATCH_SIZE, "Medellín");
        List<Technician> fourth = technicians(301, TechnicianSearchService.BATCH_SIZE, "Medellín");
        List<Technician> fifth = technicians(401, TechnicianSearchService.BATCH_SIZE, "Medellín");
        when(technicianRepository.findSearchBatch(any(), any(), anyBoolean(), any(), any(), anyBoolean(), any()))
                .thenReturn(first, second, third, fourth, fifth);

        TechnicianSearchResponse response = service.search(request(20, null));

        assertThat(response.items()).isEmpty();
        assertThat(response.nextCursor()).isEqualTo(TechnicianSearchCursor.encode(id(500)));
        verify(technicianRepository, times(TechnicianSearchService.MAX_SCAN / TechnicianSearchService.BATCH_SIZE))
                .findSearchBatch(any(), any(), anyBoolean(), any(), any(), anyBoolean(), any());
    }

    @Test
    void unTecnicoSinZonaNuncaCoincide() {
        Technician withoutZone = new Technician(id(1), NOW);
        withoutZone.replaceCategories(Set.of(ServiceCategory.PLUMBING), NOW);
        when(technicianRepository.findSearchBatch(any(), any(), anyBoolean(), any(), any(), anyBoolean(), any())).thenReturn(List.of(withoutZone));

        TechnicianSearchResponse response = service.search(request(20, null));

        assertThat(response.items()).isEmpty();
        assertThat(response.nextCursor()).isNull();
    }

    // ---------- Disponibilidad y verificación (GC-474) ----------

    private static TechnicianSearchRequest requestAt(String expectedAt) {
        return new TechnicianSearchRequest(ServiceCategory.PLUMBING, "Bogotá", 20, null, Instant.parse(expectedAt));
    }

    /** Lo que se le pasó a la consulta: filtrar por horario, día, hora y exigir verificación. */
    private record Filters(boolean filterByTime, DayOfWeek day, LocalTime time, boolean requireVerified) {
    }

    private Filters filtersUsedBy(TechnicianSearchService searchService, TechnicianSearchRequest request) {
        when(technicianRepository.findSearchBatch(any(), any(), anyBoolean(), any(), any(), anyBoolean(), any()))
                .thenReturn(List.of());
        searchService.search(request);
        ArgumentCaptor<Boolean> filterByTime = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<DayOfWeek> day = ArgumentCaptor.forClass(DayOfWeek.class);
        ArgumentCaptor<LocalTime> time = ArgumentCaptor.forClass(LocalTime.class);
        ArgumentCaptor<Boolean> requireVerified = ArgumentCaptor.forClass(Boolean.class);
        verify(technicianRepository).findSearchBatch(any(), any(), filterByTime.capture(), day.capture(),
                time.capture(), requireVerified.capture(), any(Pageable.class));
        return new Filters(filterByTime.getValue(), day.getValue(), time.getValue(), requireVerified.getValue());
    }

    @Test
    void conFechaEsperadaFiltraPorElDiaYLaHoraDeColombiaNoPorLosDeUtc() {
        // 2026-10-20T03:00Z es martes en UTC pero lunes 22:00 en Bogotá.
        Filters filters = filtersUsedBy(service, requestAt("2026-10-20T03:00:00Z"));

        assertThat(filters).isEqualTo(new Filters(true, DayOfWeek.MONDAY, LocalTime.of(22, 0), false));
    }

    @Test
    void laHoraSePasaAlSegundoParaNoDependerDeLaPrecisionDeLaBase() {
        Filters filters = filtersUsedBy(service, requestAt("2026-10-19T14:00:00.750Z"));

        assertThat(filters.time()).isEqualTo(LocalTime.of(9, 0, 0));
    }

    @Test
    void sinFechaEsperadaNoFiltraPorHorario() {
        Filters filters = filtersUsedBy(service, request(20, null));

        assertThat(filters.filterByTime()).isFalse();
    }

    @Test
    void laVerificacionSoloSeExigeSiElFlagDeLaElegibilidadEstaActivo() {
        assertThat(filtersUsedBy(service, request(20, null)).requireVerified()).isFalse();

        technicianRepository = mock(TechnicianRepository.class);
        TechnicianSearchService verifying = new TechnicianSearchService(technicianRepository, userRepository, true);
        assertThat(filtersUsedBy(verifying, request(20, null)).requireVerified()).isTrue();
    }

    @Test
    void elCursorLoEmiteEsteServicioONoSeAcepta() {
        assertThat(TechnicianSearchCursor.decode(TechnicianSearchCursor.encode(id(9)))).isEqualTo(id(9));
        assertThat(TechnicianSearchCursor.decode(null)).isEqualTo(TechnicianSearchCursor.START);
        assertThat(TechnicianSearchCursor.decode("")).isEqualTo(TechnicianSearchCursor.START);

        assertThatThrownBy(() -> service.search(request(20, "no-es-un-cursor")))
                .isInstanceOf(InvalidSearchCursorException.class);
        assertThatThrownBy(() -> TechnicianSearchCursor.decode("!!!")).isInstanceOf(InvalidSearchCursorException.class);
        // Base64 válido, pero no es un UUID escrito como lo escribe este servicio.
        assertThatThrownBy(() -> TechnicianSearchCursor.decode("MS0xLTEtMS0x"))
                .isInstanceOf(InvalidSearchCursorException.class);
    }
}
