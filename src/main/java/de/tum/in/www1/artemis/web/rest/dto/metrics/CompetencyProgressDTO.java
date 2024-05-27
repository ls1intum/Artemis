package de.tum.in.www1.artemis.web.rest.dto.metrics;

/**
 * A DTO representing a student's progress for a competency.
 */
public record CompetencyProgressDTO(long competencyId, double progress, double confidence) {
}
