package de.tum.in.www1.artemis.service.scheduled;

import de.tum.in.www1.artemis.domain.Exercise;

public interface IExerciseScheduleService<T extends Exercise> {

    void scheduleRunningExercisesOnStartup();

    void scheduleExerciseIfRequired(T exercise);

    void scheduleExercise(T exercise);
}
