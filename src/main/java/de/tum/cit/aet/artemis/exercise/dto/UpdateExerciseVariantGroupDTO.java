package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;

/**
 * Payload for updating an existing {@link ExerciseVariantGroup}. The owning course is immutable and therefore not part
 * of this DTO; only the group's own settings can be changed.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateExerciseVariantGroupDTO(@NotNull Long id, @NotBlank String title, @Nullable Double maxPoints, @Nullable ZonedDateTime releaseDate,
        @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate,
        @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate) {

    /**
     * Applies this DTO's settings to the given existing entity. The course link is intentionally left untouched.
     *
     * @param group the existing entity to update in place
     */
    public void applyTo(ExerciseVariantGroup group) {
        group.setTitle(title);
        group.setMaxPoints(maxPoints);
        group.setReleaseDate(releaseDate);
        group.setStartDate(startDate);
        group.setDueDate(dueDate);
        group.setAssessmentDueDate(assessmentDueDate);
        group.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        group.setBuildAndTestStudentSubmissionsAfterDueDate(buildAndTestStudentSubmissionsAfterDueDate);
    }
}
