package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.ParticipantScore;
import de.tum.cit.aet.artemis.assessment.domain.StudentScore;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ParticipantScoreScheduleServiceShutdownTest {

    @Test
    void scheduleTasks_whenTheSchedulerIsShutDown_doesNotFail() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ParticipantScoreRepository participantScoreRepository = mock(ParticipantScoreRepository.class);
        ResultTestRepository resultRepository = mock(ResultTestRepository.class);
        var service = new ParticipantScoreScheduleService(scheduler, Optional.empty(), participantScoreRepository, mock(StudentScoreRepository.class),
                mock(TeamScoreRepository.class), mock(ExerciseRepository.class), resultRepository, mock(UserRepository.class), mock(TeamRepository.class));
        service.activate();

        var exercise = new TextExercise();
        exercise.setId(1L);
        var user = new User();
        user.setId(2L);
        var outdatedScore = new StudentScore();
        outdatedScore.setExercise(exercise);
        outdatedScore.setUser(user);
        when(participantScoreRepository.getLatestModifiedDate()).thenReturn(Optional.of(Instant.now()));
        when(resultRepository.findAllByLastModifiedDateAfter(any())).thenReturn(List.of());
        when(participantScoreRepository.findAllOutdated()).thenReturn(List.<ParticipantScore>of(outdatedScore));
        // A scheduler that is shut down, as it is while the application context closes
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenThrow(new TaskRejectedException("ExecutorService in shutdown state did not accept task"));

        assertThatCode(service::scheduleTasks).doesNotThrowAnyException();
        verify(scheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void startup_whenTheDelayedTaskRunsAfterShutdown_doesNotReactivateTheService() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        var service = new ParticipantScoreScheduleService(scheduler, Optional.empty(), mock(ParticipantScoreRepository.class), mock(StudentScoreRepository.class),
                mock(TeamScoreRepository.class), mock(ExerciseRepository.class), mock(ResultTestRepository.class), mock(UserRepository.class), mock(TeamRepository.class));
        service.startup();
        ArgumentCaptor<Runnable> delayedStartup = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(delayedStartup.capture(), any(Instant.class));

        service.shutdown();
        delayedStartup.getValue().run();

        // an inactive service reports itself as idle
        assertThat(service.isIdle()).isTrue();
        service.scheduleTask(1L, 2L, null);
        verify(scheduler).schedule(any(Runnable.class), any(Instant.class));
    }
}
