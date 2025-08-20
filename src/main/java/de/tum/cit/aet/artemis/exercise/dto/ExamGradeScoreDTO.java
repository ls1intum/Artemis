package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamGradeScoreDTO(long participationId, long userId, long exerciseId, double score, double maxPoints, IncludedInOverallScore includedInOverallScore) {
}
