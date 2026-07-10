package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;

/**
 * Reference to an {@link ExerciseVariantGroup} embedded inside exercise DTOs. Carries the group's identity, cap and
 * shared timeline (but not its full member collection) so the client can determine group membership, display the group
 * name, and open the group-timeline edit dialog without loading the whole exercise graph. The timeline fields must stay
 * in sync with the full {@link ExerciseVariantGroupDTO} so the edit dialog does not save back missing dates and wipe the
 * shared group timeline.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseVariantGroupReferenceDTO(Long id, String title, @Nullable Double maxPoints, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate,
        @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate) {

    public static ExerciseVariantGroupReferenceDTO of(ExerciseVariantGroup group) {
        return new ExerciseVariantGroupReferenceDTO(group.getId(), group.getTitle(), group.getMaxPoints(), group.getReleaseDate(), group.getStartDate(), group.getDueDate(),
                group.getAssessmentDueDate(), group.getExampleSolutionPublicationDate(), group.getBuildAndTestStudentSubmissionsAfterDueDate());
    }
}
