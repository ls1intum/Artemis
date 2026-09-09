package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/** A privacy-minimized assessment lock entry. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LockedCourseSubmissionDTO(long id, @Nullable ZonedDateTime submissionDate, String submissionExerciseType, CourseLockedParticipationDTO participation,
        @Nullable CourseLockedResultDTO latestResult) {

    /** Maps a submission after its details have been hidden for the requesting tutor. */
    public static LockedCourseSubmissionDTO of(Submission submission) {
        return new LockedCourseSubmissionDTO(submission.getId(), submission.getSubmissionDate(), submission.getSubmissionExerciseType(),
                CourseLockedParticipationDTO.of(submission.getParticipation()), CourseLockedResultDTO.ofNullable(submission.getLatestResult()));
    }

    /** Participation identity and exercise reference used to build assessment links. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseLockedParticipationDTO(long id, @Nullable Integer submissionCount, CourseLockedExerciseDTO exercise) {

        private static CourseLockedParticipationDTO of(Participation participation) {
            return new CourseLockedParticipationDTO(participation.getId(), participation.getSubmissionCount(), CourseLockedExerciseDTO.of(participation.getExercise()));
        }
    }

    /** Minimal exercise reference for an assessment lock. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseLockedExerciseDTO(long id, String type, String title) {

        private static CourseLockedExerciseDTO of(Exercise exercise) {
            return new CourseLockedExerciseDTO(exercise.getId(), exercise.getType(), exercise.getTitle());
        }
    }

    /** Minimal result state needed to show and cancel an assessment lock. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseLockedResultDTO(@Nullable Double score, @Nullable ZonedDateTime completionDate) {

        private static @Nullable CourseLockedResultDTO ofNullable(@Nullable Result result) {
            return result == null ? null : new CourseLockedResultDTO(result.getScore(), result.getCompletionDate());
        }
    }
}
