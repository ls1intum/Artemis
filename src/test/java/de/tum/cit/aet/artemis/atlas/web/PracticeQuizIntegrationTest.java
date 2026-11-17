package de.tum.cit.aet.artemis.atlas.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionReviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionSummaryDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSubmissionDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.EntityIdRefDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.MultipleChoiceSubmittedAnswerFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;

class PracticeQuizIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "practicequiztest";

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private UserUtilService userUtilService;

    private Course course;

    private QuizExercise quizExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        course = quizExerciseUtilService.addEnrolledCourseWithOneQuizExercise("Practice Quiz Exercise", TEST_PREFIX);
        quizExercise = (QuizExercise) course.getExercises().iterator().next();
        quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExercise.getId());
        // Make quiz questions available for practice by setting due date in the past
        quizExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        quizExercise = quizExerciseRepository.save(quizExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartAndSubmitPracticeQuizFlow() throws Exception {
        // 1. Start practice session
        PracticeSessionDTO sessionDTO = request.postWithResponseBody("/api/atlas/courses/" + course.getId() + "/smart-quizzes/start", null, PracticeSessionDTO.class,
                HttpStatus.OK);

        assertThat(sessionDTO).isNotNull();
        assertThat(sessionDTO.id()).isPositive();
        assertThat(sessionDTO.questions()).isNotEmpty();

        long sessionId = sessionDTO.id();

        // 2. Check current in-progress session
        PracticeSessionDTO currentSession = request.get("/api/atlas/courses/" + course.getId() + "/smart-quizzes/current", HttpStatus.OK, PracticeSessionDTO.class);
        assertThat(currentSession).isNotNull();
        assertThat(currentSession.id()).isEqualTo(sessionId);

        // 3. Submit answers
        QuizQuestion firstQuestion = quizExercise.getQuizQuestions().getFirst();
        SubmittedAnswerFromLiveClientDTO answerDTO = new MultipleChoiceSubmittedAnswerFromLiveClientDTO(new EntityIdRefDTO(firstQuestion.getId()), java.util.Set.of());
        PracticeSubmissionDTO submission = new PracticeSubmissionDTO(List.of(answerDTO));

        PracticeSessionReviewDTO reviewDTO = request.postWithResponseBody("/api/atlas/courses/" + course.getId() + "/smart-quizzes/sessions/" + sessionId + "/submit", submission,
                PracticeSessionReviewDTO.class, HttpStatus.OK);

        assertThat(reviewDTO).isNotNull();
        assertThat(reviewDTO.id()).isEqualTo(sessionId);
        assertThat(reviewDTO.completedAt()).isNotNull();
        assertThat(reviewDTO.questions()).isNotEmpty();

        // 4. Verify review endpoint
        PracticeSessionReviewDTO fetchedReview = request.get("/api/atlas/courses/" + course.getId() + "/smart-quizzes/sessions/" + sessionId, HttpStatus.OK,
                PracticeSessionReviewDTO.class);
        assertThat(fetchedReview).isNotNull();
        assertThat(fetchedReview.id()).isEqualTo(sessionId);

        // 5. Verify sessions list
        List<PracticeSessionSummaryDTO> summaries = request.getList("/api/atlas/courses/" + course.getId() + "/smart-quizzes/sessions", HttpStatus.OK,
                PracticeSessionSummaryDTO.class);
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().id()).isEqualTo(sessionId);
        assertThat(summaries.getFirst().completed()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartPracticeQuizNoQuestionsThrowsBadRequest() throws Exception {
        // Set due date in the future so no questions are eligible for practice
        quizExercise.setDueDate(ZonedDateTime.now().plusDays(2));
        quizExerciseRepository.save(quizExercise);

        request.postWithResponseBody("/api/atlas/courses/" + course.getId() + "/smart-quizzes/start", null, PracticeSessionDTO.class, HttpStatus.BAD_REQUEST);
    }
}
