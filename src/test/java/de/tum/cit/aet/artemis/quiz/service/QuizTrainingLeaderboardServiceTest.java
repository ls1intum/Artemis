package de.tum.cit.aet.artemis.quiz.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.quiz.api.QuizQuestionApi;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgressData;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionProgressRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizTrainingLeaderboardRepository;

/**
 * Unit tests for the score delta computed by {@link QuizTrainingLeaderboardService#updateLeaderboardScore}.
 */
class QuizTrainingLeaderboardServiceTest {

    private static final long USER_ID = 1L;

    private static final long COURSE_ID = 2L;

    private QuizTrainingLeaderboardRepository leaderboardRepository;

    private QuizTrainingLeaderboardService service;

    @BeforeEach
    void setUp() {
        leaderboardRepository = mock(QuizTrainingLeaderboardRepository.class);
        var progressRepository = mock(QuizQuestionProgressRepository.class);
        var quizQuestionApi = mock(QuizQuestionApi.class);
        when(quizQuestionApi.countAllQuizQuestionsByCourseIdAvailableForPractice(COURSE_ID)).thenReturn(0L);
        when(progressRepository.countByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(0L);
        when(progressRepository.getDueDate(USER_ID, COURSE_ID)).thenReturn(Optional.empty());
        service = new QuizTrainingLeaderboardService(leaderboardRepository, mock(CourseTestRepository.class), mock(UserTestRepository.class), progressRepository, quizQuestionApi);
    }

    @Test
    void updateLeaderboardScore_correctAnswerInHigherBox_doublesScorePerBox() {
        var progress = new QuizQuestionProgressData();
        progress.setBox(4);
        progress.setLastScore(1.0);

        service.updateLeaderboardScore(USER_ID, COURSE_ID, progress);

        // 2^(4 - 1) * 1.0
        verify(leaderboardRepository).updateLeaderboardEntry(eq(USER_ID), eq(COURSE_ID), eq(8), eq(1), eq(0), any(ZonedDateTime.class));
    }

    @Test
    void updateLeaderboardScore_partialAnswerInFirstBox_roundsScore() {
        var progress = new QuizQuestionProgressData();
        progress.setBox(1);
        progress.setLastScore(0.5);

        service.updateLeaderboardScore(USER_ID, COURSE_ID, progress);

        // 2^0 * 0.5 = 0.5 rounds to 1
        verify(leaderboardRepository).updateLeaderboardEntry(eq(USER_ID), eq(COURSE_ID), eq(1), eq(0), eq(1), any(ZonedDateTime.class));
    }
}
