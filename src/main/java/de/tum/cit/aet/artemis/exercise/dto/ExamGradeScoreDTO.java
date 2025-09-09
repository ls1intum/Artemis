package de.tum.cit.aet.artemis.exercise.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamGradeScoreDTO(long participationId, long userId, long exerciseId, @Nullable Double score, double maxPoints,
        @NotNull IncludedInOverallScore includedInOverallScore) {
}
