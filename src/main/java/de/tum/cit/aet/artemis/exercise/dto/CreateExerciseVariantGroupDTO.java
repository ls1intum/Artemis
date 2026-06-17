package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;

/**
 * Payload for creating a new {@link ExerciseVariantGroup}. The owning course is taken from the request path, not the
 * body, and is immutable afterwards.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateExerciseVariantGroupDTO(@NotBlank String title, @Nullable Double maxPoints, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate) {

    /**
     * Converts this DTO into a new, unsaved {@link ExerciseVariantGroup} entity.
     *
     * @return a new entity populated with the data from this DTO
     */
    public ExerciseVariantGroup toEntity() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setTitle(title);
        group.setMaxPoints(maxPoints);
        group.setReleaseDate(releaseDate);
        group.setStartDate(startDate);
        group.setDueDate(dueDate);
        group.setAssessmentDueDate(assessmentDueDate);
        group.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        return group;
    }
}
