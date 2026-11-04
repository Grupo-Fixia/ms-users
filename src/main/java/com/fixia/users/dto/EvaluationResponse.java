package com.fixia.users.dto;

import com.fixia.users.domain.EvaluationDirection;
import com.fixia.users.domain.EvaluationSource;
import com.fixia.users.domain.ServiceEvaluation;

import java.time.Instant;
import java.util.UUID;

/** Una evaluación tal como la ven su autor y su evaluado (GC-444). El comentario no sale de aquí a otros. */
public record EvaluationResponse(
        UUID id,
        UUID serviceRequestId,
        EvaluationDirection direction,
        UUID evaluatorId,
        UUID evaluatedId,
        int rating,
        String comment,
        EvaluationSource source,
        Instant createdAt
) {

    public static EvaluationResponse from(ServiceEvaluation evaluation) {
        return new EvaluationResponse(evaluation.getId(), evaluation.getServiceRequestId(),
                evaluation.getDirection(), evaluation.getEvaluatorId(), evaluation.getEvaluatedId(),
                evaluation.getRating(), evaluation.getComment(), evaluation.getSource(), evaluation.getCreatedAt());
    }
}
