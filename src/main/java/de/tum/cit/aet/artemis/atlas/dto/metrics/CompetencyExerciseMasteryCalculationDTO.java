package de.tum.cit.aet.artemis.atlas.dto.metrics;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyExerciseMasteryCalculationDTO(long exerciseId, double maxPoints, DifficultyLevel difficulty, boolean isProgrammingExercise, double competencyLinkWeight,
        Double lastScore, Double lastPoints, Instant lastModifiedDate, long submissionCount) {
}
