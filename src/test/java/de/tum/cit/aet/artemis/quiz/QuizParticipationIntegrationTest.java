package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionFromStudentDTO;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Integration tests for {@link de.tum.cit.aet.artemis.quiz.web.QuizParticipationResource#getParticipationResult}.
 * <p>
 * These tests guard the practice-vs-graded result lookup (issue #12955, PR #12972): practice participations use
 * unrated results, so the endpoint must not filter them out by {@code rated = true}, while graded participations must
 * still return only their rated result. Before the fix this endpoint had no test coverage, which is why the bug shipped.
 */
class QuizParticipationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "quizparticipationtest";

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private QuizExerciseService quizExerciseService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ParticipationTestRepository participationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationResultForPracticeParticipationReturnsUnratedResult() throws Exception {
        QuizExercise quizExercise = createEndedCourseQuiz();
        StudentParticipation participation = createParticipation(quizExercise, TEST_PREFIX + "student1", true);
        addSubmissionWithResult(participation, false, 50D, ZonedDateTime.now().minusMinutes(1));

        String content = request.get(resultUrl(quizExercise, participation), HttpStatus.OK, String.class);

        JsonNode results = firstSubmissionResults(content);
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).get("rated").asBoolean()).isFalse();
        assertThat(results.get(0).has("id")).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationResultForPracticeParticipationWithSubmissionIdReturnsUnratedResult() throws Exception {
        QuizExercise quizExercise = createEndedCourseQuiz();
        StudentParticipation participation = createParticipation(quizExercise, TEST_PREFIX + "student1", true);
        QuizSubmission submission = addSubmissionWithResult(participation, false, 50D, ZonedDateTime.now().minusMinutes(1));

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("submissionId", String.valueOf(submission.getId()));
        String content = request.get(resultUrl(quizExercise, participation), HttpStatus.OK, String.class, params);

        JsonNode results = firstSubmissionResults(content);
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).get("rated").asBoolean()).isFalse();
        // a real (persisted) result is returned, not the empty fallback: the empty fallback also serializes rated=false
        // (primitive default) but carries no id/score, so these assertions are what actually guard against the bug.
        assertThat(results.get(0).has("id")).isTrue();
        assertThat(results.get(0).get("score").asDouble()).isEqualTo(50D);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationResultForPracticeParticipationReturnsLatestAttempt() throws Exception {
        QuizExercise quizExercise = createEndedCourseQuiz();
        StudentParticipation participation = createParticipation(quizExercise, TEST_PREFIX + "student1", true);
        // an older and a newer practice attempt; the endpoint must return the most recent unrated result
        addSubmissionWithResult(participation, false, 40D, ZonedDateTime.now().minusMinutes(5));
        addSubmissionWithResult(participation, false, 80D, ZonedDateTime.now().minusMinutes(1));

        String content = request.get(resultUrl(quizExercise, participation), HttpStatus.OK, String.class);

        JsonNode results = firstSubmissionResults(content);
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).get("rated").asBoolean()).isFalse();
        assertThat(results.get(0).get("score").asDouble()).isEqualTo(80D);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationResultForGradedParticipationReturnsRatedResultIgnoringUnrated() throws Exception {
        QuizExercise quizExercise = createEndedCourseQuiz();
        StudentParticipation participation = createParticipation(quizExercise, TEST_PREFIX + "student1", false);
        QuizSubmission submission = new QuizSubmission();
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(3));
        participationUtilService.addSubmission(participation, submission);
        // an (older) rated result plus a more recent unrated result on the same submission: graded must return the rated one
        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusMinutes(2), submission, true, true, 90D);
        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusSeconds(30), submission, false, false, 10D);

        String content = request.get(resultUrl(quizExercise, participation), HttpStatus.OK, String.class);

        JsonNode results = firstSubmissionResults(content);
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).get("rated").asBoolean()).isTrue();
        assertThat(results.get(0).get("score").asDouble()).isEqualTo(90D);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitForPracticeThenGetResultShowsUnratedResultWithAnswers() throws Exception {
        // End-to-end reproduction of the user-reported bug: submitting a practice attempt must yield a retrievable
        // result with submitted answers so the quiz view can render per-question correctness indicators.
        QuizExercise quizExercise = createEndedCourseQuiz();
        QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, true, null);
        QuizSubmissionFromStudentDTO submissionDTO = QuizSubmissionFromStudentDTO.of(quizSubmission);
        Result practiceResult = request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/practice", submissionDTO, Result.class, HttpStatus.OK);
        assertThat(practiceResult).isNotNull();

        StudentParticipation participation = participationRepository.findByExerciseId(quizExercise.getId()).stream().map(StudentParticipation.class::cast)
                .filter(StudentParticipation::isTestRun).findFirst().orElseThrow();

        String content = request.get(resultUrl(quizExercise, participation), HttpStatus.OK, String.class);

        JsonNode json = objectMapper.readTree(content);
        JsonNode submissions = json.get("submissions");
        assertThat(submissions).isNotNull();
        assertThat(submissions.size()).isEqualTo(1);
        JsonNode results = submissions.get(0).get("results");
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).get("rated").asBoolean()).isFalse();
        // the empty fallback result also serializes rated=false, so assert a real persisted result is returned
        assertThat(results.get(0).has("id")).isTrue();
        assertThat(submissions.get(0).get("submittedAnswers").size()).isGreaterThan(0);
    }

    private QuizExercise createEndedCourseQuiz() {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().minusMinutes(5), ZonedDateTime.now().minusMinutes(2), QuizMode.SYNCHRONIZED);
        quizExercise.setDuration(120);
        return quizExerciseService.save(quizExercise);
    }

    private StudentParticipation createParticipation(QuizExercise quizExercise, String login, boolean practice) {
        User student = userUtilService.getUserByLogin(login);
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(quizExercise);
        participation.setParticipant(student);
        participation.setInitializationState(InitializationState.FINISHED);
        participation.setInitializationDate(ZonedDateTime.now());
        participation.setPracticeMode(practice);
        return (StudentParticipation) participationRepository.save(participation);
    }

    private QuizSubmission addSubmissionWithResult(StudentParticipation participation, boolean rated, double score, ZonedDateTime completionDate) {
        QuizSubmission submission = new QuizSubmission();
        submission.setSubmitted(true);
        submission.setSubmissionDate(completionDate.minusSeconds(5));
        QuizSubmission savedSubmission = (QuizSubmission) participationUtilService.addSubmission(participation, submission);
        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, completionDate, savedSubmission, true, rated, score);
        return savedSubmission;
    }

    private String resultUrl(QuizExercise quizExercise, StudentParticipation participation) {
        return "/api/quiz/quiz-exercises/" + quizExercise.getId() + "/participations/" + participation.getId() + "/result";
    }

    private JsonNode firstSubmissionResults(String content) throws Exception {
        JsonNode json = objectMapper.readTree(content);
        JsonNode submissions = json.get("submissions");
        assertThat(submissions).isNotNull();
        assertThat(submissions.size()).isEqualTo(1);
        return submissions.get(0).get("results");
    }
}
