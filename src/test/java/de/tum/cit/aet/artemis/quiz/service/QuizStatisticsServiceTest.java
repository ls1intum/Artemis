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
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswerSelection;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticProjections.RatedSelection;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticsRepository;

class QuizStatisticsServiceTest {

    private WebsocketMessagingService websocketMessagingService;

    private TaskScheduler taskScheduler;

    private QuizStatisticsRepository quizStatisticsRepository;

    private LocalDataProviderService distributedDataProvider;

    private QuizStatisticsService quizStatisticsService;

    @BeforeEach
    void setUp() {
        websocketMessagingService = mock(WebsocketMessagingService.class);
        taskScheduler = mock(TaskScheduler.class);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
        quizStatisticsRepository = mock(QuizStatisticsRepository.class);
        distributedDataProvider = new LocalDataProviderService();
        quizStatisticsService = new QuizStatisticsService(quizStatisticsRepository, websocketMessagingService, taskScheduler, distributedDataProvider);
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
    void shouldCoalesceStatisticsNotificationsAcrossServiceInstances() {
        TaskScheduler otherNodeScheduler = mock(TaskScheduler.class);
        WebsocketMessagingService otherNodeMessagingService = mock(WebsocketMessagingService.class);
        QuizStatisticsService otherNodeService = new QuizStatisticsService(quizStatisticsRepository, otherNodeMessagingService, otherNodeScheduler, distributedDataProvider);

        quizStatisticsService.notifyStatisticsChanged(42L);
        otherNodeService.notifyStatisticsChanged(42L);

        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        verify(otherNodeScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void shouldAllowRetryWhenSchedulingStatisticsNotificationFails() {
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenThrow(new IllegalStateException("scheduler stopped")).thenReturn(mock(ScheduledFuture.class));

        assertThatThrownBy(() -> quizStatisticsService.notifyStatisticsChanged(42L)).isInstanceOf(IllegalStateException.class);
        quizStatisticsService.notifyStatisticsChanged(42L);

        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void shouldCalculateQuestionAggregateFromTheSelectionQuery() {
        QuizExercise quizExercise = new QuizExercise();
        quizExercise.setId(42L);
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();
        question.setId(7L);
        question.setPoints(2);
        quizExercise.addQuestion(question);

        RatedSelection ratedCorrect = selection(true, 2.0);
        RatedSelection unratedIncorrect = selection(false, 1.0);
        RatedSelection unratedMissingScore = selection(false, null);
        when(quizStatisticsRepository.findSelectionsForQuestion(7L)).thenReturn(List.of(ratedCorrect, unratedIncorrect, unratedMissingScore));

        var response = quizStatisticsService.getQuestionStatistic(quizExercise, 7L);

        assertThat(response.quizQuestionStatistic().participantsRated()).isOne();
        assertThat(response.quizQuestionStatistic().participantsUnrated()).isEqualTo(2);
        assertThat(response.quizQuestionStatistic().ratedCorrectCounter()).isOne();
        assertThat(response.quizQuestionStatistic().unRatedCorrectCounter()).isZero();
        verify(quizStatisticsRepository).findSelectionsForQuestion(7L);
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

    private static RatedSelection selection(boolean rated, Double scoreInPoints) {
        RatedSelection selection = mock(RatedSelection.class);
        when(selection.getRated()).thenReturn(rated);
        when(selection.getScoreInPoints()).thenReturn(scoreInPoints);
        when(selection.getSelection()).thenReturn(new MultipleChoiceSubmittedAnswerSelection());
        return selection;
    }
}
