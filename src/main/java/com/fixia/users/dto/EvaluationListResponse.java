package com.fixia.users.dto;

import java.util.List;

/** Evaluaciones de varios servicios en las que quien consulta evaluó o fue evaluado (GC-444). */
public record EvaluationListResponse(List<EvaluationResponse> items) {
}
