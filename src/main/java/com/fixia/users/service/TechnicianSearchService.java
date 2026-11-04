package com.fixia.users.service;

import com.fixia.users.domain.AvailabilitySlot;
import com.fixia.users.domain.CoverageZone;
import com.fixia.users.domain.Technician;
import com.fixia.users.domain.User;
import com.fixia.users.dto.TechnicianSearchRequest;
import com.fixia.users.dto.TechnicianSearchResponse;
import com.fixia.users.repository.TechnicianRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Técnicos que ofrecen una categoría y atienden una ciudad, para ms-matching-geo. Los datos del técnico viven en esta
 * base (Database per Service), por eso la búsqueda se resuelve aquí y quien consulta solo recibe el resultado.
 *
 * <p>La categoría y el estado de la cuenta se filtran en la base; la ciudad se compara en Java con
 * {@link CoverageZone#matchesCity}, que ignora tildes y mayúsculas igual que la elegibilidad. El círculo de la zona
 * no filtra aquí: viaja en la respuesta para que ms-matching-geo lo aplique con las coordenadas de la solicitud.
 *
 * <p><b>Disponibilidad y verificación (GC-474).</b> Con {@code expectedAt} solo se devuelven los técnicos con una franja
 * que incluya ese momento en hora de Colombia, la misma regla que la elegibilidad ({@link AvailabilitySlot#includes}):
 * sin franjas declaradas, un técnico no aparece. La verificación vigente solo se exige si
 * {@code fixia.eligibility.require-verified} está activo, el mismo flag de la elegibilidad (por defecto, no). Ambos
 * filtros van en la consulta, no en Java, para no cargar las franjas de cada técnico revisado.
 *
 * <p><b>Paginación por cursor.</b> Igual que las oportunidades de ms-services (ADR-044), una consulta revisa como
 * máximo {@link #MAX_SCAN} técnicos, en lotes de {@link #BATCH_SIZE}. Si no reúne {@code limit} resultados devuelve los
 * que encontró y un cursor con la posición alcanzada, de modo que recorrer las páginas hasta que el cursor sea nulo
 * entrega todos los técnicos compatibles sin que el costo de una consulta crezca con el total.
 */
@Service
public class TechnicianSearchService {

    /** Técnicos que se leen de la base por lote. */
    static final int BATCH_SIZE = 100;

    /** Técnicos que se revisan como máximo en una consulta (múltiplo de {@link #BATCH_SIZE}). */
    static final int MAX_SCAN = 500;

    private final TechnicianRepository technicianRepository;
    private final UserRepository userRepository;
    private final boolean requireVerified;

    public TechnicianSearchService(TechnicianRepository technicianRepository, UserRepository userRepository,
                                   @Value("${fixia.eligibility.require-verified:false}") boolean requireVerified) {
        this.technicianRepository = technicianRepository;
        this.userRepository = userRepository;
        this.requireVerified = requireVerified;
    }

    @Transactional(readOnly = true)
    public TechnicianSearchResponse search(TechnicianSearchRequest request) {
        int limit = request.effectiveLimit();
        UUID position = TechnicianSearchCursor.decode(request.cursor());

        // Sin expectedAt no se filtra por horario; el día y la hora que se pasan entonces son de relleno y no se usan.
        boolean filterByTime = request.expectedAt() != null;
        ZonedDateTime local = filterByTime ? request.expectedAt().atZone(AvailabilitySlot.ZONE) : null;
        DayOfWeek day = filterByTime ? local.getDayOfWeek() : DayOfWeek.MONDAY;
        LocalTime time = filterByTime ? local.toLocalTime().truncatedTo(ChronoUnit.SECONDS) : LocalTime.MIDNIGHT;

        List<Technician> matches = new ArrayList<>();
        int scanned = 0;
        boolean exhausted = false;

        scan:
        while (scanned < MAX_SCAN) {
            List<Technician> batch = technicianRepository.findSearchBatch(
                    position, request.category(), filterByTime, day, time, requireVerified,
                    PageRequest.of(0, BATCH_SIZE));
            boolean lastBatch = batch.size() < BATCH_SIZE;
            for (int i = 0; i < batch.size(); i++) {
                Technician technician = batch.get(i);
                scanned++;
                position = technician.getUserId();
                CoverageZone zone = technician.getCoverageZone();
                if (zone != null && zone.matchesCity(request.city())) {
                    matches.add(technician);
                }
                if (matches.size() == limit) {
                    // Si se llenó la página en el último técnico de un lote corto, ya no queda nada por revisar.
                    exhausted = lastBatch && i == batch.size() - 1;
                    break scan;
                }
            }
            if (lastBatch) {
                exhausted = true;
                break;
            }
        }

        return new TechnicianSearchResponse(items(matches), exhausted ? null : TechnicianSearchCursor.encode(position));
    }

    /** Arma los resultados en el orden de la búsqueda, con una consulta por tabla para todo el lote. */
    private List<TechnicianSearchResponse.Item> items(List<Technician> matches) {
        if (matches.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = matches.stream().map(Technician::getUserId).toList();
        Map<UUID, Technician> withCategories = technicianRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(Technician::getUserId, Function.identity()));
        Map<UUID, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<TechnicianSearchResponse.Item> items = new ArrayList<>();
        for (Technician match : matches) {
            Technician technician = withCategories.get(match.getUserId());
            User user = users.get(match.getUserId());
            if (technician == null || user == null) {
                continue;
            }
            CoverageZone zone = technician.getCoverageZone();
            TechnicianSearchResponse.Circle circle = zone.hasCircle()
                    ? new TechnicianSearchResponse.Circle(zone.getLatitude(), zone.getLongitude(),
                            zone.getRadiusKm())
                    : null;
            items.add(new TechnicianSearchResponse.Item(
                    technician.getUserId(),
                    TechnicianPublicProfileService.displayName(user.getFirstName(), user.getLastName()),
                    List.copyOf(technician.getCategories()),
                    zone.getCity(),
                    circle));
        }
        return items;
    }
}
