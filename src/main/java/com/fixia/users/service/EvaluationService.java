package com.fixia.users.service;

import com.fixia.users.client.ServiceParticipationClient;
import com.fixia.users.client.ServiceParticipationClient.Participation;
import com.fixia.users.config.EvaluationProperties;
import com.fixia.users.domain.EvaluationDirection;
import com.fixia.users.domain.Role;
import com.fixia.users.domain.ServiceEvaluation;
import com.fixia.users.exception.EvaluableServiceNotFoundException;
import com.fixia.users.exception.EvaluationConflictException;
import com.fixia.users.exception.EvaluationRejectedException;
import com.fixia.users.repository.OutboxEventRepository;
import com.fixia.users.repository.ServiceEvaluationRepository;
import com.fixia.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Evaluaciones bidireccionales de servicios cerrados (GC-444, contrato de GC-443).
 *
 * <p>El orden de una evaluación es: preguntar a ms-services quién participó (fuera de toda transacción, porque es una
 * llamada de red), deducir la dirección y el evaluado, responder un reintento si ya existe, aplicar las reglas y
 * guardar la evaluación con su evento en una sola transacción. La restricción única de la base
 * decide entre dos envíos simultáneos; el que pierde relee la ganadora y responde como un reintento o como conflicto.
 */
@Service
public class EvaluationService {

    /** Resultado de registrar: la evaluación y si se creó ahora (201) o ya existía igual (200). */
    public record Result(ServiceEvaluation evaluation, boolean created) {
    }

    private record Side(EvaluationDirection direction, UUID evaluatedId) {
    }

    private final ServiceParticipationClient participationClient;
    private final ServiceEvaluationRepository evaluationRepository;
    private final OutboxEventRepository outboxRepository;
    private final UserRepository userRepository;
    private final EvaluationEvents events;
    private final EvaluationProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public EvaluationService(ServiceParticipationClient participationClient,
                             ServiceEvaluationRepository evaluationRepository, OutboxEventRepository outboxRepository,
                             UserRepository userRepository, EvaluationEvents events, EvaluationProperties properties,
                             TransactionTemplate transactionTemplate, Clock clock) {
        this.participationClient = participationClient;
        this.evaluationRepository = evaluationRepository;
        this.outboxRepository = outboxRepository;
        this.userRepository = userRepository;
        this.events = events;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * @param role rol con el que actúa quien evalúa: {@code CLIENT} o {@code PROFESSIONAL}
     * @throws EvaluableServiceNotFoundException si el servicio no existe o quien evalúa no participó en él
     * @throws EvaluationRejectedException       si el servicio no está cerrado, es de una empresa, venció el plazo o
     *                                           el evaluado ya no existe
     * @throws EvaluationConflictException       si ya hay otra evaluación distinta en esa dirección
     * @throws com.fixia.users.exception.ServicesUnavailableException si ms-services no responde
     */
    public Result register(UUID evaluatorId, Role role, UUID serviceRequestId, int rating, String comment) {
        Participation participation = participationClient.find(serviceRequestId)
                .orElseThrow(EvaluableServiceNotFoundException::new);
        Side side = sideOf(participation, evaluatorId, role);

        Optional<ServiceEvaluation> existing =
                evaluationRepository.findByServiceRequestIdAndDirection(serviceRequestId, side.direction());
        if (existing.isPresent()) {
            return repeated(existing.get(), evaluatorId, rating, comment);
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!participation.isClosed()) {
            throw new EvaluationRejectedException(EvaluationRejectedException.SERVICE_NOT_CLOSED);
        }
        if (participation.isCompanyProvider()) {
            throw new EvaluationRejectedException(EvaluationRejectedException.COMPANY_SERVICE_NOT_EVALUABLE);
        }
        if (now.isAfter(participation.closedAt().plus(properties.window()))) {
            throw new EvaluationRejectedException(EvaluationRejectedException.EVALUATION_WINDOW_EXPIRED);
        }
        if (!userRepository.existsById(side.evaluatedId())) {
            throw new EvaluationRejectedException(EvaluationRejectedException.EVALUATED_USER_NOT_FOUND);
        }

        // PostgreSQL guarda microsegundos: se trunca antes para que la fila y el evento digan lo mismo.
        ServiceEvaluation evaluation = ServiceEvaluation.register(serviceRequestId, side.direction(), evaluatorId,
                side.evaluatedId(), rating, comment, participation.closedAt().truncatedTo(ChronoUnit.MICROS), now);
        try {
            ServiceEvaluation saved = transactionTemplate.execute(status -> {
                ServiceEvaluation stored = evaluationRepository.saveAndFlush(evaluation);
                outboxRepository.save(events.registered(stored, role));
                return stored;
            });
            return new Result(saved, true);
        } catch (DataIntegrityViolationException e) {
            // Otro envío ganó la restricción única entre la consulta y el insert.
            ServiceEvaluation winner = evaluationRepository
                    .findByServiceRequestIdAndDirection(serviceRequestId, side.direction())
                    .orElseThrow(() -> e);
            return repeated(winner, evaluatorId, rating, comment);
        }
    }

    /** Las evaluaciones de esos servicios en las que el usuario evaluó o fue evaluado. */
    @Transactional(readOnly = true)
    public List<ServiceEvaluation> visibleTo(UUID userId, Collection<UUID> serviceRequestIds) {
        return evaluationRepository.findVisibleTo(userId, serviceRequestIds);
    }

    /**
     * Quien evalúa como cliente debe ser el cliente del servicio, y como técnico, el prestador seleccionado. Cualquier
     * otra combinación es un tercero: 404, igual que un servicio inexistente.
     */
    private static Side sideOf(Participation participation, UUID evaluatorId, Role role) {
        if (role == Role.CLIENT && evaluatorId.equals(participation.clientId())) {
            return new Side(EvaluationDirection.CLIENT_TO_PROVIDER, participation.providerId());
        }
        if (role == Role.PROFESSIONAL && evaluatorId.equals(participation.providerId())) {
            return new Side(EvaluationDirection.PROVIDER_TO_CLIENT, participation.clientId());
        }
        throw new EvaluableServiceNotFoundException();
    }

    private static Result repeated(ServiceEvaluation existing, UUID evaluatorId, int rating, String comment) {
        if (existing.isSameAs(evaluatorId, rating, comment)) {
            return new Result(existing, false);
        }
        throw new EvaluationConflictException();
    }
}
