package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;

/**
 * DTO returned for a newly created {@link UserStoryExercise}: what the client needs to navigate to it and render it,
 * without serializing the exercise graph behind it.
 * <p>
 * The timeline is the group's - a user story never has one of its own - and is reported here so the client does not have
 * to refetch the group to show the created story. {@code type} is the exercise's discriminator ({@code "user-story"}), so
 * the client can resolve its route through the same {@code getExerciseUrlSegment} mapping as any other exercise.
 *
 * @param milestoneGroupId the id of the {@code MilestoneExerciseGroup} the story was created in
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserStoryExerciseDTO(long id, String title, String shortName, String type, @Nullable Long courseId, @Nullable Long milestoneGroupId, @Nullable String channelName,
        @Nullable Double maxPoints, @Nullable Double bonusPoints, @Nullable DifficultyLevel difficulty, @Nullable String problemStatement, @Nullable ZonedDateTime releaseDate,
        @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate) {

    public UserStoryExerciseDTO(UserStoryExercise exercise) {
        this(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getType(),
                exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null,
                exercise.getExerciseVariantGroup() != null ? exercise.getExerciseVariantGroup().getId() : null, exercise.getChannelName(), exercise.getMaxPoints(),
                exercise.getBonusPoints(), exercise.getDifficulty(), exercise.getProblemStatement(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(),
                exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate());
    }
}
