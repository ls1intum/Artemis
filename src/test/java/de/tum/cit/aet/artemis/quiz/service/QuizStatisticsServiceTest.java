package de.tum.cit.aet.artemis.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticsRepository;

class QuizStatisticsServiceTest {

    private WebsocketMessagingService websocketMessagingService;

    private TaskScheduler taskScheduler;

    private QuizStatisticsRepository quizStatisticsRepository;

    private QuizStatisticsService quizStatisticsService;

    @BeforeEach
    void setUp() {
        websocketMessagingService = mock(WebsocketMessagingService.class);
        taskScheduler = mock(TaskScheduler.class);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
        quizStatisticsRepository = mock(QuizStatisticsRepository.class);
        quizStatisticsService = new QuizStatisticsService(quizStatisticsRepository, websocketMessagingService, taskScheduler);
    }

    @Test
    void shouldCoalesceStatisticsNotificationsForSameQuizDuringDebounceWindow() {
        quizStatisticsService.notifyStatisticsChanged(42L);
        quizStatisticsService.notifyStatisticsChanged(42L);

        ArgumentCaptor<Runnable> notification = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(notification.capture(), any(Instant.class));
        verify(websocketMessagingService, never()).sendMessage(any(), any());

        notification.getValue().run();

        verify(websocketMessagingService).sendMessage("/topic/statistic/42", 42L);

        quizStatisticsService.notifyStatisticsChanged(42L);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void shouldAllowRetryWhenSchedulingStatisticsNotificationFails() {
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenThrow(new IllegalStateException("scheduler stopped")).thenReturn(mock(ScheduledFuture.class));

        assertThatThrownBy(() -> quizStatisticsService.notifyStatisticsChanged(42L)).isInstanceOf(IllegalStateException.class);
        quizStatisticsService.notifyStatisticsChanged(42L);

        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void shouldPrepopulatePointBucketsThroughRoundedOverallPoints() {
        var quizExercise = mock(QuizExercise.class);
        when(quizExercise.getId()).thenReturn(42L);
        when(quizExercise.getOverallQuizPoints()).thenReturn(2.5);
        when(quizStatisticsRepository.findPointStatistic(42L)).thenReturn(List.of());

        var statistics = quizStatisticsService.getPointStatistic(quizExercise);

        assertThat(statistics.quizPointStatistic().pointCounters()).hasSize(4);
    }
}
