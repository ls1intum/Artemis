package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import de.tum.cit.aet.artemis.core.util.Pair;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseLifecycle;
import de.tum.cit.aet.artemis.exercise.service.ExerciseLifecycleService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationLifecycleService;
import de.tum.cit.aet.artemis.lecture.api.SlideLifecycleServiceApi;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.domain.SlideLifecycle;
import de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

class ScheduleServiceLifecycleTest {

    private final ExerciseLifecycleService exercises = mock(ExerciseLifecycleService.class);

    private final ParticipationLifecycleService participations = mock(ParticipationLifecycleService.class);

    private final SlideLifecycleServiceApi slides = mock(SlideLifecycleServiceApi.class);

    private final Runnable task = () -> {
    };

    private MockedConstruction<ThreadPoolTaskScheduler> construction;

    private ScheduleService service;

    @BeforeEach
    void setUp() {
        construction = mockConstruction(ThreadPoolTaskScheduler.class);
        service = new ScheduleService(exercises, participations, Optional.of(slides));
    }

    @AfterEach
    void tearDown() {
        construction.close();
    }

    @Test
    void exerciseReplacementCancelsWithoutInterruptingAndPagesByScheduledTime() {
        var exercise = exercise(17);
        var oldFuture = future(Future.State.RUNNING, 500);
        var replacement = future(Future.State.RUNNING, 100);
        var earlier = future(Future.State.SUCCESS, 5);
        doReturn(oldFuture, replacement).when(exercises).scheduleTask(exercise, ExerciseLifecycle.RELEASE, task);
        doReturn(earlier).when(exercises).scheduleTask(exercise, ExerciseLifecycle.START, task);
        service.scheduleExerciseTask(exercise, ExerciseLifecycle.RELEASE, task, "old");
        service.scheduleExerciseTask(exercise, ExerciseLifecycle.RELEASE, new Pair<>(ZonedDateTime.now(), task), "replacement");
        service.scheduleExerciseTask(exercise, ExerciseLifecycle.START, task, "early");
        verify(oldFuture).cancel(false);
        var first = service.findAllExerciseEvents(PageRequest.of(0, 1));
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getContent()).singleElement().satisfies(event -> {
            assertThat(event.exerciseId()).isEqualTo(17);
            assertThat(event.name()).isEqualTo("early");
            assertThat(event.lifecycle()).isEqualTo(ExerciseLifecycle.START);
            assertThat(event.state()).isEqualTo(Future.State.SUCCESS);
        });
        assertThat(service.findAllExerciseEvents(PageRequest.of(1, 1)).getContent()).singleElement().extracting(ScheduleService.ScheduledExerciseEvent::name)
                .isEqualTo("replacement");
        assertThat(service.findAllExerciseEvents(PageRequest.of(3, 1)).getContent()).isEmpty();
        service.cancelScheduledTaskForLifecycle(17L, ExerciseLifecycle.RELEASE);
        verify(replacement).cancel(false);
        assertThat(service.findAllExerciseEvents(PageRequest.of(0, 10)).getContent()).extracting(ScheduleService.ScheduledExerciseEvent::name).containsExactly("early");
    }

    @Test
    void quizBatchUsesItsSpecificSchedulingOverload() {
        var quiz = new QuizExercise();
        quiz.setId(23L);
        var batch = new QuizBatch();
        var future = future(Future.State.RUNNING, 1);
        doReturn(future).when(exercises).scheduleTask(quiz, batch, ExerciseLifecycle.START, task);
        service.scheduleExerciseTask(quiz, batch, ExerciseLifecycle.START, task, "batch start");
        assertThat(service.findAllExerciseEvents(PageRequest.of(0, 5)).getContent()).singleElement().extracting(ScheduleService.ScheduledExerciseEvent::exerciseId).isEqualTo(23L);
        service.clearAllTasks();
        verify(future).cancel(true);
        assertThat(service.findAllExerciseEvents(PageRequest.of(0, 5)).getContent()).isEmpty();
    }

    @Test
    void optionalSlideModuleProducesNoEventsAndConfiguredModuleReplacesAndCancels() {
        var slide = new Slide();
        slide.setId(9L);
        new ScheduleService(exercises, participations, Optional.empty()).scheduleSlideTask(slide, SlideLifecycle.UNHIDE, task, "disabled");
        var first = future(Future.State.RUNNING, 30);
        var second = future(Future.State.RUNNING, 10);
        doReturn(first, second).when(slides).scheduleTask(slide, SlideLifecycle.UNHIDE, task);
        service.scheduleSlideTask(slide, SlideLifecycle.UNHIDE, task, "old");
        service.scheduleSlideTask(slide, SlideLifecycle.UNHIDE, task, "unhide");
        verify(first).cancel(false);
        assertThat(service.findAllSlideEvents(PageRequest.of(0, 5)).getContent()).singleElement().satisfies(event -> {
            assertThat(event.slideId()).isEqualTo(9L);
            assertThat(event.lifecycle()).isEqualTo(SlideLifecycle.UNHIDE);
            assertThat(event.name()).isEqualTo("unhide");
        });
        assertThat(service.findAllSlideEvents(PageRequest.of(5, 1)).getContent()).isEmpty();
        service.cancelScheduledTaskForSlideLifecycle(9L, SlideLifecycle.UNHIDE);
        service.cancelScheduledTaskForSlideLifecycle(9L, SlideLifecycle.UNHIDE);
        verify(second).cancel(false);
        assertThat(service.findAllSlideEvents(PageRequest.of(0, 5)).getContent()).isEmpty();
    }

    @Test
    void participationReplacementAndCancelAllRespectAbsentSchedules() {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(82L);
        participation.setExercise(exercise(17));
        var first = future(Future.State.RUNNING, 5);
        var second = future(Future.State.RUNNING, 10);
        when(participations.scheduleTask(participation, ParticipationLifecycle.DUE, task)).thenReturn(Optional.of(first), Optional.of(second));
        when(participations.scheduleTask(participation, ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, task)).thenReturn(Optional.empty());
        service.scheduleParticipationTask(participation, ParticipationLifecycle.DUE, task, "due");
        service.scheduleParticipationTask(participation, ParticipationLifecycle.DUE, task, "replaced");
        service.scheduleParticipationTask(participation, ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, task, "absent");
        verify(first).cancel(false);
        service.cancelAllScheduledParticipationTasks(17L, 82L);
        verify(second).cancel(false);
        service.clearAllTasks();
        verify(first, never()).cancel(true);
        verify(second, never()).cancel(true);
    }

    @Test
    void periodicMaintenanceRetainsRunningTasksAndPrunesCompletedTasksAcrossModules() {
        var exercise = exercise(17);
        var running = future(Future.State.RUNNING, 90);
        var done = future(Future.State.SUCCESS, -1);
        doReturn(running).when(exercises).scheduleTask(exercise, ExerciseLifecycle.RELEASE, task);
        doReturn(done).when(exercises).scheduleTask(exercise, ExerciseLifecycle.START, task);
        var slide = new Slide();
        slide.setId(9L);
        var completedSlide = future(Future.State.CANCELLED, 0);
        doReturn(completedSlide).when(slides).scheduleTask(slide, SlideLifecycle.UNHIDE, task);
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(82L);
        participation.setExercise(exercise);
        var completedParticipation = future(Future.State.FAILED, 0);
        when(participations.scheduleTask(participation, ParticipationLifecycle.DUE, task)).thenReturn(Optional.of(completedParticipation));
        service.startup();
        var maintenance = ArgumentCaptor.forClass(Runnable.class);
        verify(construction.constructed().getFirst()).scheduleAtFixedRate(maintenance.capture(), eq(Duration.ofSeconds(15)));
        maintenance.getValue().run();
        service.scheduleExerciseTask(exercise, ExerciseLifecycle.RELEASE, task, "running");
        service.scheduleExerciseTask(exercise, ExerciseLifecycle.START, task, "done");
        service.scheduleSlideTask(slide, SlideLifecycle.UNHIDE, task, "completed slide");
        service.scheduleParticipationTask(participation, ParticipationLifecycle.DUE, task, "completed participation");
        maintenance.getValue().run();
        maintenance.getValue().run();
        assertThat(service.findAllExerciseEvents(PageRequest.of(0, 5)).getContent()).singleElement().extracting(ScheduleService.ScheduledExerciseEvent::name).isEqualTo("running");
        assertThat(service.findAllSlideEvents(PageRequest.of(0, 5)).getContent()).isEmpty();
        service.clearAllTasks();
        verify(running).cancel(true);
        verify(done, never()).cancel(true);
        verify(completedParticipation, never()).cancel(true);
        verify(completedSlide, never()).cancel(true);
    }

    private ProgrammingExercise exercise(long id) {
        var exercise = new ProgrammingExercise();
        exercise.setId(id);
        return exercise;
    }

    private ScheduledFuture<?> future(Future.State state, long delay) {
        var future = mock(ScheduledFuture.class);
        when(future.state()).thenReturn(state);
        when(future.getDelay(TimeUnit.SECONDS)).thenReturn(delay);
        return future;
    }
}
