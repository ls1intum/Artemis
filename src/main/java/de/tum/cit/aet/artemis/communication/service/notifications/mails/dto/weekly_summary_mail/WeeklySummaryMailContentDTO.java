package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.weekly_summary_mail;

import java.util.Set;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.core.service.TimeService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

public record WeeklySummaryMailContentDTO(boolean hasNewExercises, Set<WeeklyExerciseSummaryDTO> exerciseSummaries) {

    public static WeeklySummaryMailContentDTO of(Set<Exercise> exercises, TimeService timeService) {
        boolean hasNewExercises = !exercises.isEmpty();
        Set<WeeklyExerciseSummaryDTO> exerciseSummaries = exercises.stream().map(exercise -> WeeklyExerciseSummaryDTO.of(exercise, timeService)).collect(Collectors.toSet());
        return new WeeklySummaryMailContentDTO(hasNewExercises, exerciseSummaries);
    }
}
