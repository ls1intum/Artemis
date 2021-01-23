package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.NEW_RESULT_RESOURCE_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.TestResultsDTO;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.ProgrammingSubmissionResource;
import de.tum.in.www1.artemis.web.rest.ResultResource;

public class ProgrammingSubmissionAndResultGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String ARTEMIS_AUTHENTICATION_TOKEN_VALUE;

    @Autowired
    ProgrammingExerciseRepository exerciseRepo;

    @Autowired
    ProgrammingSubmissionResource programmingSubmissionResource;

    @Autowired
    ResultResource resultResource;

    @Autowired
    ProgrammingSubmissionRepository submissionRepository;

    @Autowired
    ParticipationRepository participationRepository;

    @Autowired
    ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    @Autowired
    SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Autowired
    TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    BuildLogEntryRepository buildLogEntryRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();

        database.addUsers(3, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(0);
        database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        database.addStudentParticipationForProgrammingExercise(exercise, "student2");

        exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        jenkinsRequestMockProvider.reset();
        gitlabRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateFeedbackInSemiAutomaticResult() throws Exception {
        SecurityUtils.setAuthorizationObject();
        // Required for created a result
        var resultAccessor = database.getUserByLogin("instructor1");

        // Add a student submission with two manual results and a semi automatic result
        var participation = studentParticipationRepository.findByExerciseId(exercise.getId()).get(0);
        var submission = database.createProgrammingSubmission(participation, false);
        database.addResultToSubmission(submission, AssessmentType.MANUAL, resultAccessor);
        database.addResultToSubmission(submission, AssessmentType.MANUAL, resultAccessor);
        database.addResultToSubmission(submission, AssessmentType.SEMI_AUTOMATIC, resultAccessor);

        // Add a manual feedback to the semi automatic result
        var feedback = new Feedback();
        feedback.setType(FeedbackType.MANUAL);
        feedback.setText("feedback1");
        feedback.setCredits(10.0);

        var resultsWithFeedback = resultRepository.findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(exercise.getId());
        var semiAutoResult = resultsWithFeedback.get(2);
        database.addFeedbackToResult(feedback, semiAutoResult);

        // Assert that the results have been created successfully.
        resultsWithFeedback = resultRepository.findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(exercise.getId());
        assertThat(resultsWithFeedback.size()).isEqualTo(3);
        assertThat(resultsWithFeedback.get(0).getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(resultsWithFeedback.get(1).getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(resultsWithFeedback.get(2).getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);

        // Re-trigger the build. We create a notification with feedback of a successful test
        database.changeUser("instructor1");
        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), "student1", exercise.getProgrammingLanguage(), List.of("test1"));
        postResult(notification);

        // Retrieve updated results
        var updatedResults = resultRepository.findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(exercise.getId());
        assertThat(updatedResults.size()).isEqualTo(3);

        // Assert that the result order stays the same
        assertThat(updatedResults.get(0).getId()).isEqualTo(resultsWithFeedback.get(0).getId());
        assertThat(updatedResults.get(1).getId()).isEqualTo(resultsWithFeedback.get(1).getId());
        assertThat(updatedResults.get(2).getId()).isEqualTo(resultsWithFeedback.get(2).getId());

        // Assert that the last result is the SEMI_AUTOMATIC result
        semiAutoResult = updatedResults.get(2);
        assertThat(semiAutoResult.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        // Assert that the SEMI_AUTOMATIC result has two feedbacks whereas the last one is the automatic one
        assertThat(semiAutoResult.getFeedbacks().size()).isEqualTo(2);
        assertThat(semiAutoResult.getFeedbacks().get(0).getType()).isEqualTo(FeedbackType.MANUAL);
        assertThat(semiAutoResult.getFeedbacks().get(1).getType()).isEqualTo(FeedbackType.AUTOMATIC);

    }

    private static Stream<Arguments> shouldSavebuildLogsOnStudentParticipationArguments() {
        return Arrays.stream(ProgrammingLanguage.values())
                .flatMap(programmingLanguage -> Stream.of(Arguments.of(programmingLanguage, true), Arguments.of(programmingLanguage, false)));
    }

    @ParameterizedTest
    @MethodSource("shouldSavebuildLogsOnStudentParticipationArguments")
    void shouldNotReceiveBuildLogsOnStudentParticipationWithoutResult(ProgrammingLanguage programmingLanguage, boolean enableStaticCodeAnalysis) throws Exception {
        // Precondition: Database has participation and a programming submission.
        String userLogin = "student1";
        database.addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, programmingLanguage);
        ProgrammingExercise exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(1);
        var participation = database.addStudentParticipationForProgrammingExercise(exercise, userLogin);
        var submission = database.createProgrammingSubmission(participation, false);

        // Call programming-exercises/new-result which do not include build log entries yet
        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, programmingLanguage, List.of());
        postResult(notification);

        var result = assertBuildError(participation.getId(), userLogin);
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());

        // Call again and assert that no new submissions have been created
        postResult(notification);
        assertNoNewSubmissions(submission);
    }

    @ParameterizedTest
    @MethodSource("shouldSavebuildLogsOnStudentParticipationArguments")
    void shouldNotReceiveBuildLogsOnStudentParticipationWithoutSubmissionNorResult(ProgrammingLanguage programmingLanguage, boolean enableStaticCodeAnalysis) throws Exception {
        // Precondition: Database has participation without result and a programming
        String userLogin = "student1";
        database.addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, programmingLanguage);
        ProgrammingExercise exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(1);
        var participation = database.addStudentParticipationForProgrammingExercise(exercise, userLogin);

        // Call programming-exercises/new-result which do not include build log entries yet
        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, programmingLanguage, List.of());
        postResult(notification);

        assertBuildError(participation.getId(), userLogin);
    }

    private Result assertBuildError(Long participationId, String userLogin) throws Exception {
        SecurityUtils.setAuthorizationObject();

        // Assert that result is linked to the participation
        var results = resultRepository.findAllByParticipationIdOrderByCompletionDateDesc(participationId);
        assertThat(results.size()).isEqualTo(1);
        var result = results.get(0);
        assertThat(result.getHasFeedback()).isFalse();
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getResultString()).isEqualTo("No tests found");

        // Assert that the submission linked to the participation
        var submission = (ProgrammingSubmission) result.getSubmission();
        assertThat(submission).isNotNull();
        assertThat(submission.isBuildFailed()).isTrue();

        var submissionWithLogsOptional = submissionRepository.findWithEagerBuildLogEntriesById(submission.getId());
        assertThat(submissionWithLogsOptional).isPresent();

        // Assert that the submission does not contain build log entries yet
        var submissionWithLogs = submissionWithLogsOptional.get();
        assertThat(submissionWithLogs.getBuildLogEntries()).hasSize(0);

        // Assert that the build logs can be retrieved from the REST API
        var buildWithDetails = jenkinsRequestMockProvider.mockGetLatestBuildLogs(studentParticipationRepository.findById(participationId).get());
        database.changeUser(userLogin);
        var receivedLogs = request.get("/api/repository/" + participationId + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotNull();
        assertThat(receivedLogs.size()).isGreaterThan(0);

        verify(buildWithDetails, times(1)).getConsoleOutputHtml();

        // Call again and it should not call Jenkins::getLatestBuildLogs() since the logs are cached.
        receivedLogs = request.get("/api/repository/" + participationId + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotNull();
        assertThat(receivedLogs.size()).isGreaterThan(0);

        verify(buildWithDetails, times(1)).getConsoleOutputHtml();

        return result;
    }

    private void assertNoNewSubmissions(ProgrammingSubmission existingSubmission) {
        var updatedSubmissions = submissionRepository.findAll();
        assertThat(updatedSubmissions.size()).isEqualTo(1);
        assertThat(updatedSubmissions.get(0).getId()).isEqualTo(existingSubmission.getId());
    }

    private void postResult(TestResultsDTO requestBodyMap) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        final var alteredObj = mapper.convertValue(requestBodyMap, Object.class);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", ARTEMIS_AUTHENTICATION_TOKEN_VALUE);
        request.postWithoutLocation("/api" + NEW_RESULT_RESOURCE_PATH, alteredObj, HttpStatus.OK, httpHeaders);
    }

    private TestResultsDTO createJenkinsNewResultNotification(String projectKey, String loginName, ProgrammingLanguage programmingLanguage, List<String> successfullTests) {
        var repoName = (projectKey + "-" + loginName).toUpperCase();
        // The full name is specified as <FOLDER NAME> » <JOB NAME> <Build Number>
        var fullName = exercise.getProjectKey() + " » " + repoName + " #3";
        var notification = ModelFactory.generateTestResultDTO(repoName, successfullTests, List.of(), programmingLanguage, false);
        notification.setFullName(fullName);
        return notification;
    }
}
