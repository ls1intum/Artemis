package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;

/**
 * Payload for creating a new {@link ExerciseVariantGroup}. The owning course is taken from the request path, not the
 * body, and is immutable afterwards.
 *
 * Serializable because it is embedded — via the variant-generation request's placement — in the Hazelcast-distributed
 * {@code VariantJob} record.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateExerciseVariantGroupDTO(@NotBlank @Size(max = 255) String title, @Nullable @PositiveOrZero Double maxPoints, @Nullable ZonedDateTime releaseDate,
        @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate)
        implements Serializable {

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
