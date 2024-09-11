package de.tum.cit.aet.artemis.web.rest.dto.metrics;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.enumeration.DifficultyLevel;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyExerciseMasteryCalculationDTO(double maxPoints, DifficultyLevel difficulty, boolean isProgrammingExercise, Double lastScore, Double lastPoints,
        Instant lastModifiedDate, long submissionCount) {
}
