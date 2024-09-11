package de.tum.cit.aet.artemis.web.rest.dto.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing a student's progress for a competency.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyProgressDTO(long competencyId, double progress, double confidence) {
}
