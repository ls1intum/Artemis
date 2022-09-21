package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.NEW_RESULT_RESOURCE_PATH;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.JAVA;
import static de.tum.in.www1.artemis.programmingexercise.ProgrammingSubmissionConstants.GITLAB_PUSH_EVENT_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
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
import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.CommitDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.TestResultsDTO;
import de.tum.in.www1.artemis.util.ModelFactory;

class ProgrammingSubmissionAndResultGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String ARTEMIS_AUTHENTICATION_TOKEN_VALUE;

    @Autowired
    private ProgrammingSubmissionRepository submissionRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ResultRepository resultRepository;

    private ProgrammingExercise exercise;

    @Autowired
    private ProgrammingSubmissionAndResultIntegrationTestService testService;

    @BeforeEach
    void setUp() {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();

        database.addUsers(3, 2, 0, 2);
        var course = database.addCourseWithOneProgrammingExerciseAndTestCases();

        exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(exercise.getId()).get();

        database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        database.addStudentParticipationForProgrammingExercise(exercise, "student2");

        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(exercise.getId()).get();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
        jenkinsRequestMockProvider.reset();
        gitlabRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldReceiveBuildLogsOnNewStudentParticipationResult() throws Exception {
        // Precondition: Database has participation and a programming submission.
        String userLogin = "student1";
        var course = database.addCourseWithOneProgrammingExercise(false, false, ProgrammingLanguage.JAVA);
        var exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(exercise.getId()).get();

        var participation = database.addStudentParticipationForProgrammingExercise(exercise, userLogin);
        var submission = database.createProgrammingSubmission(participation, false);

        List<String> logs = new ArrayList<>();
        logs.add("[2021-05-10T15:19:49.740Z] [ERROR] BubbleSort.java:[15,9] not a statement");
        logs.add("[2021-05-10T15:19:49.740Z] [ERROR] BubbleSort.java:[15,10] ';' expected");

        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, ProgrammingLanguage.JAVA, List.of());
        notification.setLogs(logs);
        postResult(notification, HttpStatus.OK);

        var submissionWithLogsOptional = submissionRepository.findWithEagerBuildLogEntriesById(submission.getId());
        assertThat(submissionWithLogsOptional).isPresent();

        // Assert that the submission contains build log entries
        ProgrammingSubmission submissionWithLogs = submissionWithLogsOptional.get();
        List<BuildLogEntry> buildLogEntries = submissionWithLogs.getBuildLogEntries();
        assertThat(buildLogEntries).hasSize(2);
        assertThat(buildLogEntries.get(0).getLog()).isEqualTo("[ERROR] BubbleSort.java:[15,9] not a statement");
        assertThat(buildLogEntries.get(1).getLog()).isEqualTo("[ERROR] BubbleSort.java:[15,10] ';' expected");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldParseLegacyBuildLogsWhenPipelineLogsNotPresent() throws Exception {
        // Precondition: Database has participation and a programming submission.
        String userLogin = "student1";
        var course = database.addCourseWithOneProgrammingExercise(false, false, ProgrammingLanguage.JAVA);
        var exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(exercise.getId()).get();

        var participation = database.addStudentParticipationForProgrammingExercise(exercise, userLogin);
        database.createProgrammingSubmission(participation, true);

        jenkinsRequestMockProvider.mockGetLegacyBuildLogs(participation);
        database.changeUser(userLogin);
        var receivedLogs = request.get("/api/repository/" + participation.getId() + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotEmpty();
    }

    private static Stream<Arguments> shouldSaveBuildLogsOnStudentParticipationArguments() {
        return Arrays.stream(ProgrammingLanguage.values())
                .flatMap(programmingLanguage -> Stream.of(Arguments.of(programmingLanguage, true), Arguments.of(programmingLanguage, false)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("shouldSaveBuildLogsOnStudentParticipationArguments")
    @WithMockUser(username = "student1", roles = "USER")
    void shouldReturnBadRequestWhenPlanKeyDoesntExist(ProgrammingLanguage programmingLanguage, boolean enableStaticCodeAnalysis) throws Exception {
        // Precondition: Database has participation and a programming submission.
        String userLogin = "student1";
        var course = database.addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, programmingLanguage);
        exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(exercise.getId()).get();

        var participation = database.addStudentParticipationForProgrammingExercise(exercise, userLogin);

        // Call programming-exercises/new-result which do not include build log entries yet
        var notification = createJenkinsNewResultNotification("scrambled build plan key", userLogin, programmingLanguage, List.of());
        postResult(notification, HttpStatus.BAD_REQUEST);

        var results = resultRepository.findAllByParticipationIdOrderByCompletionDateDesc(participation.getId());
        assertThat(results).isEmpty();
    }

    /**
     * This test results from a bug where the first push event wasn't received by Artemis but all build events.
     * This test ensures that in such a situation, the submission dates are set according to the commit dates and are therefore in the correct order.
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived() throws Exception {
        testService.setUp_shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived();
        String userLogin = "student1";
        var pushJSON = (JSONObject) new JSONParser().parse(GITLAB_PUSH_EVENT_REQUEST);
        var firstCommitHash = (String) pushJSON.get("before");
        var secondCommitHash = (String) pushJSON.get("after");
        var firstCommitDate = ZonedDateTime.now().minusSeconds(60);
        var secondCommitDate = ZonedDateTime.now().minusSeconds(30);

        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        gitlabRequestMockProvider.mockGetPushDate(testService.participation, Map.of(firstCommitHash, firstCommitDate, secondCommitHash, secondCommitDate));

        // First commit is pushed but not recorded

        // Second commit is pushed and recorded
        postSubmission(testService.participation.getId(), HttpStatus.OK);

        // Build result for first commit is received
        var firstBuildCompleteDate = ZonedDateTime.now();
        var firstVcsDTO = new CommitDTO();
        firstVcsDTO.setRepositorySlug(urlService.getRepositorySlugFromRepositoryUrl(testService.participation.getVcsRepositoryUrl()));
        firstVcsDTO.setHash(firstCommitHash);
        var notificationDTOFirstCommit = createJenkinsNewResultNotification(testService.programmingExercise.getProjectKey(), userLogin, JAVA, List.of());
        notificationDTOFirstCommit.setRunDate(firstBuildCompleteDate);
        notificationDTOFirstCommit.setCommits(List.of(firstVcsDTO));

        postResult(notificationDTOFirstCommit, HttpStatus.OK);

        // Build result for second commit is received
        var secondBuildCompleteDate = ZonedDateTime.now();
        var secondVcsDTO = new CommitDTO();
        secondVcsDTO.setRepositorySlug(urlService.getRepositorySlugFromRepositoryUrl(testService.participation.getVcsRepositoryUrl()));
        secondVcsDTO.setHash(secondCommitHash);
        var notificationDTOSecondCommit = createJenkinsNewResultNotification(testService.programmingExercise.getProjectKey(), userLogin, JAVA, List.of());
        notificationDTOSecondCommit.setRunDate(secondBuildCompleteDate);
        notificationDTOSecondCommit.setCommits(List.of(secondVcsDTO));

        postResult(notificationDTOSecondCommit, HttpStatus.OK);

        testService.shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived(firstCommitHash, firstCommitDate, secondCommitHash, secondCommitDate);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("shouldSaveBuildLogsOnStudentParticipationArguments")
    @WithMockUser(username = "student1", roles = "USER")
    void shouldNotReceiveBuildLogsOnStudentParticipationWithoutResult(ProgrammingLanguage programmingLanguage, boolean enableStaticCodeAnalysis) throws Exception {
        // Precondition: Database has participation and a programming submission.
        String userLogin = "student1";
        var course = database.addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, programmingLanguage);
        exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(exercise.getId()).get();

        var participation = database.addStudentParticipationForProgrammingExercise(exercise, userLogin);
        var submission = database.createProgrammingSubmission(participation, false);

        // Call programming-exercises/new-result which do not include build log entries yet
        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, programmingLanguage, List.of());
        postResult(notification, HttpStatus.OK);

        var result = assertBuildError(participation.getId(), userLogin, false);
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());

        // Call again and assert that no new submissions have been created
        postResult(notification, HttpStatus.OK);
        assertNoNewSubmissions(submission);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("shouldSaveBuildLogsOnStudentParticipationArguments")
    @WithMockUser(username = "student1", roles = "USER")
    void shouldNotReceiveBuildLogsOnStudentParticipationWithoutSubmissionNorResult(ProgrammingLanguage programmingLanguage, boolean enableStaticCodeAnalysis) throws Exception {
        // Precondition: Database has participation without result and a programming
        String userLogin = "student1";
        var course = database.addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, programmingLanguage);
        exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(exercise.getId()).get();

        var participation = database.addStudentParticipationForProgrammingExercise(exercise, userLogin);

        // Call programming-exercises/new-result which do not include build log entries yet
        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, programmingLanguage, List.of());
        postResult(notification, HttpStatus.OK);

        assertBuildError(participation.getId(), userLogin, true);
    }

    private Result assertBuildError(Long participationId, String userLogin, boolean useLegacyBuildLogs) throws Exception {
        SecurityUtils.setAuthorizationObject();

        // Assert that result is linked to the participation
        var results = resultRepository.findAllByParticipationIdOrderByCompletionDateDesc(participationId);
        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.getHasFeedback()).isFalse();
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getScore()).isEqualTo(0D);

        // Assert that the submission linked to the participation
        var submission = (ProgrammingSubmission) result.getSubmission();
        assertThat(submission).isNotNull();
        assertThat(submission.isBuildFailed()).isTrue();

        var submissionWithLogsOptional = submissionRepository.findWithEagerBuildLogEntriesById(submission.getId());
        assertThat(submissionWithLogsOptional).isPresent();

        // Assert that the submission does not contain build log entries yet
        var submissionWithLogs = submissionWithLogsOptional.get();
        assertThat(submissionWithLogs.getBuildLogEntries()).isEmpty();

        // Assert that the build logs can be retrieved from the REST API
        var buildWithDetails = jenkinsRequestMockProvider.mockGetLatestBuildLogs(studentParticipationRepository.findById(participationId).get(), useLegacyBuildLogs);
        database.changeUser(userLogin);
        var receivedLogs = request.get("/api/repository/" + participationId + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotNull().isNotEmpty();

        if (useLegacyBuildLogs) {
            verify(buildWithDetails, times(1)).getConsoleOutputHtml();
        }
        else {
            verify(buildWithDetails, times(1)).getConsoleOutputText();
        }

        // Call again and it should not call Jenkins::getLatestBuildLogs() since the logs are cached.
        receivedLogs = request.get("/api/repository/" + participationId + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotNull().isNotEmpty();

        if (useLegacyBuildLogs) {
            verify(buildWithDetails, times(1)).getConsoleOutputHtml();
        }
        else {
            verify(buildWithDetails, times(1)).getConsoleOutputText();
        }

        return result;
    }

    /**
     * This is the simulated request from the VCS to Artemis on a new commit.
     */
    private ProgrammingSubmission postSubmission(Long participationId, HttpStatus expectedStatus) throws Exception {
        return testService.postSubmission(participationId, expectedStatus, GITLAB_PUSH_EVENT_REQUEST);
    }

    private void assertNoNewSubmissions(ProgrammingSubmission existingSubmission) {
        var updatedSubmissions = submissionRepository.findAll();
        assertThat(updatedSubmissions).hasSize(1);
        assertThat(updatedSubmissions.get(0).getId()).isEqualTo(existingSubmission.getId());
    }

    private void postResult(TestResultsDTO requestBodyMap, HttpStatus status) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        final var alteredObj = mapper.convertValue(requestBodyMap, Object.class);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", ARTEMIS_AUTHENTICATION_TOKEN_VALUE);
        request.postWithoutLocation("/api/" + NEW_RESULT_RESOURCE_PATH, alteredObj, status, httpHeaders);
    }

    private TestResultsDTO createJenkinsNewResultNotification(String projectKey, String loginName, ProgrammingLanguage programmingLanguage, List<String> successfulTests) {
        var repoName = (projectKey + "-" + loginName).toUpperCase();
        // The full name is specified as <FOLDER NAME> » <JOB NAME> <Build Number>
        var fullName = exercise.getProjectKey() + " » " + repoName + " #3";
        var notification = ModelFactory.generateTestResultDTO(repoName, successfulTests, List.of(), programmingLanguage, false);
        notification.setFullName(fullName);
        return notification;
    }

}
