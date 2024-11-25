package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.weekly_summary_mail;

import de.tum.cit.aet.artemis.core.service.TimeService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

public record WeeklyExerciseSummaryDTO(String title, String type, String difficulty, String releaseDate, String dueDate, double maxPoints, double bonusPoints) {

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
