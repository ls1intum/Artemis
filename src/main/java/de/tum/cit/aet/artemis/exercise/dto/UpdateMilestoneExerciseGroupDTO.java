package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;

/**
 * Payload for updating a {@link MilestoneExerciseGroup}'s title and shared timeline.
 * <p>
 * Carries no {@code maxPoints}: a milestone group's points are always the sum of its members' (see
 * {@link MilestoneExerciseGroup#setMaxPoints}). The dates land on the group's anchor exercise, since the group delegates
 * its timeline there.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateMilestoneExerciseGroupDTO(@NotNull Long id, @NotBlank @Size(max = 255) String title, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate) {

    /**
     * Applies this DTO's settings to the given existing group. The course link is intentionally left untouched.
     *
     * @param group the existing group to update in place
     */
    public void applyTo(MilestoneExerciseGroup group) {
        group.setTitle(title);
        group.setReleaseDate(releaseDate);
        group.setStartDate(startDate);
        group.setDueDate(dueDate);
        group.setAssessmentDueDate(assessmentDueDate);
        group.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
    }
}
