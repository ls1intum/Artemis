package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;

/**
 * Reference to an {@link ExerciseVariantGroup} embedded inside exercise DTOs. Carries the group's identity, cap and
 * shared timeline (but not its member collection) so the client can show membership and open the group-timeline edit
 * dialog without loading the whole exercise graph. The timeline fields must stay in sync with {@link ExerciseVariantGroupDTO}
 * so the edit dialog does not save back missing dates and wipe the shared timeline.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseVariantGroupReferenceDTO(Long id, String title, @Nullable Double maxPoints, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate,
        @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate) {

    /**
     * Maps an exercise's variant group for an exercise DTO, or {@code null} when the exercise has no group.
     * <p>
     * {@link de.tum.cit.aet.artemis.exercise.domain.Exercise#getExerciseVariantGroup()} is a LAZY association, so a read
     * path that does not fetch it hands back an uninitialized proxy. Reading a field off that proxy after the session has
     * closed (open-in-view is off) throws {@code LazyInitializationException} and the request fails with a 500 — which is
     * how the student quiz participation endpoint broke. Callers must therefore never map the association blindly; going
     * through this method degrades an unfetched group to {@code null} instead. Endpoints whose response genuinely needs
     * the group fetch-join it, and are covered by tests asserting it is present in the serialized response.
     *
     * @param group the exercise's variant group; may be {@code null} or an unfetched proxy
     * @return the reference, or {@code null} when there is no group or it was not loaded
     */
    public static @Nullable ExerciseVariantGroupReferenceDTO ofNullable(@Nullable ExerciseVariantGroup group) {
        if (group == null || !Hibernate.isInitialized(group)) {
            return null;
        }
        return new ExerciseVariantGroupReferenceDTO(group.getId(), group.getTitle(), group.getMaxPoints(), group.getReleaseDate(), group.getStartDate(), group.getDueDate(),
                group.getAssessmentDueDate(), group.getExampleSolutionPublicationDate(), group.getBuildAndTestStudentSubmissionsAfterDueDate());
    }
}
