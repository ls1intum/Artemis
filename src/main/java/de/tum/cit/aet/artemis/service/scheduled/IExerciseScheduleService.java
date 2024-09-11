package de.tum.cit.aet.artemis.service.scheduled;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Interface for exercise specific schedulers.
 *
 * @param <T> extends Exercise
 */
public interface IExerciseScheduleService<T extends Exercise> {

    /**
     * Method that is used to reschedule tasks on startup (schedule information is kept in memory).
     */
    void scheduleRunningExercisesOnStartup();

    /**
     * Schedules / does not schedule the task with the given exercise settings.
     * Will also cancel existing tasks if they exist.
     *
     * @param exercise Exercise
     */
    void updateScheduling(T exercise);
}
