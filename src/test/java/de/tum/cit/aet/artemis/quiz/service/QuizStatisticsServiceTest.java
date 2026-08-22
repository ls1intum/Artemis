package de.tum.cit.aet.artemis.quiz.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticsRepository;

class QuizStatisticsServiceTest {

    private WebsocketMessagingService websocketMessagingService;

    private TaskScheduler taskScheduler;

    private QuizStatisticsService quizStatisticsService;

    @BeforeEach
    void setUp() {
        websocketMessagingService = mock(WebsocketMessagingService.class);
        taskScheduler = mock(TaskScheduler.class);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
        quizStatisticsService = new QuizStatisticsService(mock(QuizStatisticsRepository.class), websocketMessagingService, taskScheduler);
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
}
