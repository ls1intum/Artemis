package de.tum.cit.aet.artemis.exercise.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseGradeScoreDTO(long participationId, long userId, long exerciseId, double score, @Nullable Double presentationScore, @Nullable ExerciseType type) {

    // Constructor for queries that don't include ExerciseType
    public CourseGradeScoreDTO(long participationId, long userId, long exerciseId, double score, @Nullable Double presentationScore) {
        this(participationId, userId, exerciseId, score, presentationScore, null);
    }
}
