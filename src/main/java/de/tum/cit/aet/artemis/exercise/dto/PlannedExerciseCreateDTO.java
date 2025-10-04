package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import de.tum.cit.aet.artemis.exercise.domain.PlannedExercise;

@JsonInclude(Include.NON_EMPTY)
public record PlannedExerciseCreateDTO(@NotNull String title, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate,
        @Nullable ZonedDateTime assessmentDueDate) {

    public static PlannedExercise toDomainObject(PlannedExerciseCreateDTO dto) {
        PlannedExercise plannedExercise = new PlannedExercise();
        plannedExercise.setTitle(dto.title);
        plannedExercise.setReleaseDate(dto.releaseDate);
        plannedExercise.setStartDate(dto.startDate);
        plannedExercise.setDueDate(dto.dueDate);
        plannedExercise.setAssessmentDueDate(dto.assessmentDueDate);
        return plannedExercise;
    }
}
