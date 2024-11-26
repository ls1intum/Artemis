package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.weekly_summary_mail;

import java.util.Set;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.core.service.TimeService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * A Data Transfer Object (DTO) representing the content of a weekly summary email.
 *
 * @param hasNewExercises   indicates if there are new exercises in the summary
 * @param exerciseSummaries a set of summaries for each exercise included in the weekly summary
 */
public record WeeklySummaryMailContentDTO(boolean hasNewExercises, Set<WeeklyExerciseSummaryDTO> exerciseSummaries) {

    /**
     * Creates a new instance of WeeklySummaryMailContentDTO from a set of exercises.
     *
     * @param exercises   the set of exercises to be included in the summary
     * @param timeService the service used to convert dates to a human-readable format
     * @return a new instance of WeeklySummaryMailContentDTO
     */
    public static WeeklySummaryMailContentDTO of(Set<Exercise> exercises, TimeService timeService) {
        boolean hasNewExercises = !exercises.isEmpty();
        Set<WeeklyExerciseSummaryDTO> exerciseSummaries = exercises.stream().map(exercise -> WeeklyExerciseSummaryDTO.of(exercise, timeService)).collect(Collectors.toSet());
        return new WeeklySummaryMailContentDTO(hasNewExercises, exerciseSummaries);
    }
}
