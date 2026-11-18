package de.tum.cit.aet.artemis.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseLifecycle;
import de.tum.cit.aet.artemis.exercise.service.ExerciseLifecycleService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationLifecycleService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ScheduleServiceTest {

    private ExerciseLifecycleService exerciseLifecycleService;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        exerciseLifecycleService = mock(ExerciseLifecycleService.class);
        scheduleService = new ScheduleService(exerciseLifecycleService, mock(ParticipationLifecycleService.class), Optional.empty());
    }

    @AfterEach
    void tearDown() {
        scheduleService.clearAllTasks();
    }

    @Test
    void reschedulingAnExerciseTaskDoesNotInterruptTheRunningTask() {
        var exercise = new TextExercise();
        exercise.setId(1L);
        ScheduledFuture<?> firstFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondFuture = mock(ScheduledFuture.class);
        doReturn(firstFuture, secondFuture).when(exerciseLifecycleService).scheduleTask(eq(exercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));

        scheduleService.scheduleExerciseTask(exercise, ExerciseLifecycle.DUE, () -> {
        }, "first");
        scheduleService.scheduleExerciseTask(exercise, ExerciseLifecycle.DUE, () -> {
        }, "second");

        verify(firstFuture).cancel(false);
        verify(firstFuture, never()).cancel(true);
    }
}
