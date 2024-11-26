package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.weekly_summary_mail;

import de.tum.cit.aet.artemis.core.service.TimeService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * A Data Transfer Object (DTO) representing a summary of an exercise for the weekly summary email.
 *
 * @param title       the title of the exercise
 * @param type        the type of the exercise
 * @param difficulty  the difficulty level of the exercise
 * @param releaseDate the release date of the exercise in a human-readable format
 * @param dueDate     the due date of the exercise in a human-readable format
 * @param maxPoints   the maximum points that can be earned for the exercise
 * @param bonusPoints the bonus points that can be earned for the exercise
 */
public record WeeklyExerciseSummaryDTO(String title, String type, String difficulty, String releaseDate, String dueDate, double maxPoints, double bonusPoints) {

    /**
     * Creates a new `WeeklyExerciseSummaryDTO` instance from the given `Exercise` instance.
     * The release and due dates are converted to human-readable format using the given `TimeService`.
     *
     * @param exercise    the exercise to be summarized
     * @param timeService the service to convert dates to human-readable format
     * @return a new `WeeklyExerciseSummaryDTO` instance
     */
    public static WeeklyExerciseSummaryDTO of(Exercise exercise, TimeService timeService) {
        String releaseDate = null, dueDate = null;
        if (exercise.getReleaseDate() != null) {
            releaseDate = timeService.convertToHumanReadableDate(exercise.getReleaseDate());
        }
        if (exercise.getDueDate() != null) {
            dueDate = timeService.convertToHumanReadableDate(exercise.getDueDate());
        }

        String difficulty = exercise.getDifficulty() != null ? exercise.getDifficulty().toString() : null;

        return new WeeklyExerciseSummaryDTO(exercise.getTitle(), exercise.getType(), difficulty, releaseDate, dueDate, exercise.getMaxPoints(), exercise.getBonusPoints());
    }
}
