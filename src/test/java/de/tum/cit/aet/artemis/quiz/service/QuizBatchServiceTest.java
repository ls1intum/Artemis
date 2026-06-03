package de.tum.cit.aet.artemis.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;

class QuizBatchServiceTest {

    private QuizBatchService quizBatchService;

    @BeforeEach
    void setUp() {
        quizBatchService = new QuizBatchService(mock(QuizBatchRepository.class), mock(QuizSubmissionRepository.class));
    }

    @Test
    void quizBatchStartDateShouldKeepTargetTimeWithoutDueDate() {
        var quizExercise = new QuizExercise();
        quizExercise.setDuration(7 * 24 * 60 * 60);
        var targetTime = ZonedDateTime.parse("2026-06-02T12:00:00Z");

        assertThat(quizBatchService.quizBatchStartDate(quizExercise, targetTime)).isEqualTo(targetTime);
    }

    @Test
    void quizBatchStartDateShouldKeepTargetTimeWhenBatchDoesNotOverrunDueDate() {
        var dueDate = ZonedDateTime.parse("2026-06-09T12:00:00Z");
        var targetTime = dueDate.minusDays(8);
        var quizExercise = new QuizExercise();
        quizExercise.setDuration(7 * 24 * 60 * 60);
        quizExercise.setDueDate(dueDate);

        assertThat(quizBatchService.quizBatchStartDate(quizExercise, targetTime)).isEqualTo(targetTime);
    }

    @Test
    void quizBatchStartDateShouldClampWeekLongBatchToDueDate() {
        var dueDate = ZonedDateTime.parse("2026-06-09T12:00:00Z");
        var targetTime = ZonedDateTime.parse("2026-06-02T12:00:00Z");
        var quizExercise = new QuizExercise();
        quizExercise.setDuration(7 * 24 * 60 * 60);
        quizExercise.setDueDate(dueDate);

        var expectedStartTime = dueDate.minusSeconds(quizExercise.getDuration() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS);

        assertThat(quizBatchService.quizBatchStartDate(quizExercise, targetTime)).isEqualTo(expectedStartTime);
    }
}
