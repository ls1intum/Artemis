package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisQuizTimerDTO;
import de.tum.cit.aet.artemis.iris.service.IrisAssessmentReviewService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

/**
 * HTTP-contract tests for {@link de.tum.cit.aet.artemis.iris.web.IrisAskUserResource}.
 */
class IrisAskUserResourceIntegrationTest extends AbstractIrisChatSessionTest {

    private static final String TEST_PREFIX = "irisaskuserresource";

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ResultTestRepository resultTestRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private IrisAssessmentReviewService irisAssessmentReviewService;

    @Autowired
    private ExamUtilService examUtilService;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    private String baseUrl() {
        return "/api/iris/programming-exercises/" + programmingExercise.getId() + "/ask-user/";
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startQuizForCurrentSessionSucceedsWhenAskUserModeEnabled() throws Exception {
        request.patch(baseUrl() + "start", null, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startQuizForCurrentSessionFailsWhenAskUserModeDisabled() throws Exception {
        disableAskUserMode();

        request.patch(baseUrl() + "start", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startQuizForCurrentSessionFailsWhenExerciseIsExamExercise() throws Exception {
        ProgrammingExercise examProgrammingExercise = createExamProgrammingExercise();

        request.patch("/api/iris/programming-exercises/" + examProgrammingExercise.getId() + "/ask-user/start", null, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startInClassQuizFailsWhenNoInClassWindowIsActive() throws Exception {
        request.patch(baseUrl() + "in-class/start", null, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startInClassQuizSucceedsWhenInClassWindowIsActive() throws Exception {
        irisAssessmentReviewService.makeInClassQuizAvailable(programmingExercise);

        request.patch(baseUrl() + "in-class/start", null, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void registerDefocusIsNoOpWhenQuizNotActive() throws Exception {
        request.patch(baseUrl() + "defocus", null, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startTimerReturnsZeroedTimerWhenQuizNotActive() throws Exception {
        var timer = request.patchWithResponseBody(baseUrl() + "start-timer", null, IrisQuizTimerDTO.class, HttpStatus.OK);

        assertThat(timer.timerExpiresAt()).isNull();
        assertThat(timer.timeLimit()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void stopTimerForCurrentSessionSucceedsWhenQuizNotActive() throws Exception {
        request.patch(baseUrl() + "stop-timer", null, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void latestSubmissionHasPointsIsFalseWithoutAnySubmission() throws Exception {
        // student2 has no pre-existing participation, unlike student1 which the shared test fixture already seeds with participations.
        var hasPoints = request.get(baseUrl() + "latest-submission-has-points", HttpStatus.OK, Boolean.class);

        assertThat(hasPoints).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void latestSubmissionHasPointsIsTrueAfterPositiveScoreSubmissionBeforeDueDate() throws Exception {
        // student2 has no pre-existing participation, unlike student1 which the shared test fixture already seeds with participations.
        addSubmissionWithScore(TEST_PREFIX + "student2", 80.0);

        var hasPoints = request.get(baseUrl() + "latest-submission-has-points", HttpStatus.OK, Boolean.class);

        assertThat(hasPoints).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void isQuizAlreadyDoneIsFalseWithoutAssessment() throws Exception {
        var alreadyDone = request.get(baseUrl() + "completed", HttpStatus.OK, Boolean.class);

        assertThat(alreadyDone).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void isQuizStartedForExerciseIsFalseWhenNoQuizWasStarted() throws Exception {
        var started = request.get(baseUrl() + "is-quiz-started", HttpStatus.OK, Boolean.class);

        assertThat(started).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void isInClassQuizStartedForExerciseIsFalseWhenNoQuizWasStarted() throws Exception {
        var started = request.get(baseUrl() + "in-class/is-quiz-started", HttpStatus.OK, Boolean.class);

        assertThat(started).isFalse();
    }

    private ProgrammingExercise createExamProgrammingExercise() {
        var exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, true);
        ProgrammingExercise examProgrammingExercise = exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream())
                .filter(ProgrammingExercise.class::isInstance).map(ProgrammingExercise.class::cast).findFirst().orElseThrow();
        activateIrisFor(examProgrammingExercise);
        return examProgrammingExercise;
    }

    private void disableAskUserMode() {
        var current = irisSettingsService.getSettingsForCourse(course);
        irisSettingsService.updateCourseSettings(course.getId(), IrisCourseSettings.of(current.enabled(), false, current.askUserModeSettings(), current.customInstructions(),
                current.variant(), current.supportLevel(), current.rateLimit()), true);
    }

    private ProgrammingSubmission addSubmissionWithScore(String login, double score) {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, login);
        var submission = new ProgrammingSubmission();
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmissionDate(ZonedDateTime.now());
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        var result = new Result();
        result.setScore(score);
        submission.addResult(result);
        result.setSubmission(submission);
        result.setExerciseId(programmingExercise.getId());

        submission = programmingSubmissionRepository.save(submission);
        resultTestRepository.save(result);
        return submission;
    }
}
