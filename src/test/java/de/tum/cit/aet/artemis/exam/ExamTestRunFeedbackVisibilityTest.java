package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserDestination;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * An instructor who conducts an exam test run is the participant of that run and has to see exactly what a student sees: test cases that stay hidden until the results are
 * released must be suppressed. Instructors keep full visibility everywhere else.
 */
class ExamTestRunFeedbackVisibilityTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "examtestrunfeedbackvisibility";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ResultWebsocketService resultWebsocketService;

    private ProgrammingExercise examExercise;

    private ProgrammingExerciseTestCase alwaysVisibleTestCase;

    private ProgrammingExerciseTestCase hiddenUntilReleaseTestCase;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        examExercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneProgrammingExercise(TEST_PREFIX);
        List<ProgrammingExerciseTestCase> testCases = programmingExerciseUtilService.addTestCasesToProgrammingExercise(examExercise);
        alwaysVisibleTestCase = testCases.getFirst();
        hiddenUntilReleaseTestCase = testCases.getLast();
        assertThat(alwaysVisibleTestCase.getVisibility()).isNotEqualTo(hiddenUntilReleaseTestCase.getVisibility());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void instructorConductingATestRunDoesNotSeeTestCasesHiddenUntilResultsAreReleased() throws Exception {
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        examUtilService.generateTestRunForInstructor(examExercise.getExerciseGroup().getExam(), instructor, List.of(examExercise));
        StudentParticipation testRunParticipation = studentParticipationRepository
                .findWithEagerSubmissionsByExerciseIdAndStudentIdAndTestRun(examExercise.getId(), instructor.getId(), true).orElseThrow();
        Submission testRunSubmission = participationUtilService.addSubmission(testRunParticipation, new ProgrammingSubmission());
        Result result = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now(), testRunSubmission);
        TestCaseFeedback visibleFeedback = participationUtilService.addTestCaseFeedbackToResult(result, alwaysVisibleTestCase, true, null);
        participationUtilService.addTestCaseFeedbackToResult(result, hiddenUntilReleaseTestCase, true, null);

        List<FeedbackDTO> feedbacks = getResultDetails(testRunParticipation, result);

        assertThat(feedbacks).extracting(FeedbackDTO::id).containsExactly(ProgrammingFeedbackSynthesizerService.syntheticTestCaseId(visibleFeedback.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void instructorStillSeesTestCasesHiddenUntilResultsAreReleasedOfAStudentParticipation() throws Exception {
        ProgrammingSubmission studentSubmission = programmingExerciseUtilService.addProgrammingSubmission(examExercise,
                (ProgrammingSubmission) new ProgrammingSubmission().submitted(true), TEST_PREFIX + "student1");
        StudentParticipation studentParticipation = (StudentParticipation) studentSubmission.getParticipation();
        assertThat(studentParticipation.isTestRun()).isFalse();
        Result result = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now(), studentSubmission);
        participationUtilService.addTestCaseFeedbackToResult(result, alwaysVisibleTestCase, true, null);
        participationUtilService.addTestCaseFeedbackToResult(result, hiddenUntilReleaseTestCase, true, null);

        List<FeedbackDTO> feedbacks = getResultDetails(studentParticipation, result);

        assertThat(feedbacks).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void newResultPushedToInstructorConductingATestRunDoesNotContainTestCasesHiddenUntilResultsAreReleased() throws Exception {
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        examUtilService.generateTestRunForInstructor(examExercise.getExerciseGroup().getExam(), instructor, List.of(examExercise));
        StudentParticipation participationWithSubmissions = studentParticipationRepository
                .findWithEagerSubmissionsByExerciseIdAndStudentIdAndTestRun(examExercise.getId(), instructor.getId(), true).orElseThrow();
        Submission testRunSubmission = participationUtilService.addSubmission(participationWithSubmissions, new ProgrammingSubmission());
        StudentParticipation testRunParticipation = studentParticipationRepository.findWithEagerExerciseContextByExerciseIdAndStudentId(examExercise.getId(), instructor.getId())
                .orElseThrow();
        Result savedResult = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now(), testRunSubmission);
        participationUtilService.addTestCaseFeedbackToResult(savedResult, alwaysVisibleTestCase, true, null);
        participationUtilService.addTestCaseFeedbackToResult(savedResult, hiddenUntilReleaseTestCase, true, null);
        Result result = resultRepository.findByIdWithEagerFeedbacksElseThrow(savedResult.getId());
        result.setSubmission(testRunSubmission);
        testRunSubmission.setParticipation(testRunParticipation);

        resultWebsocketService.broadcastNewResult(testRunParticipation, result);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService).sendMessageToUser(eq(instructor.getLogin()), any(WebsocketUserDestination.class), payload.capture());
        ResultDTO pushedResult = (ResultDTO) payload.getValue();
        assertThat(pushedResult.feedbacks()).hasSize(1);
        assertThat(pushedResult.feedbacks().getFirst().testCase().id()).isEqualTo(alwaysVisibleTestCase.getId());
    }

    private List<FeedbackDTO> getResultDetails(StudentParticipation participation, Result result) throws Exception {
        return request.getList("/api/assessment/participations/" + participation.getId() + "/results/" + result.getId() + "/details", HttpStatus.OK, FeedbackDTO.class);
    }
}
