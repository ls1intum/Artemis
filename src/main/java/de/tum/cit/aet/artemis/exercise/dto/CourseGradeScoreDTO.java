package de.tum.cit.aet.artemis.exercise.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseGradeScoreDTO(long participationId, long userId, long exerciseId, double score, @Nullable Double presentationScore, @NotNull ExerciseType type) {

}
