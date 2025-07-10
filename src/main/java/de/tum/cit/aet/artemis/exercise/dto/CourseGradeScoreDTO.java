package de.tum.cit.aet.artemis.exercise.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseGradeScoreDTO(long participationId, long userId, long exerciseId, double score, @Nullable Double presentationScore) {
}
