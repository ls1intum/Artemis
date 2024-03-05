package de.tum.in.www1.artemis.exercise.programmingexercise;

import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.JAVA;
import static de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingSubmissionConstants.GITLAB_PUSH_EVENT_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import com.google.gson.JsonParser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.ci.notification.dto.*;
import de.tum.in.www1.artemis.user.UserUtilService;

class ProgrammingSubmissionAndResultGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "progsubresgitlabjen";

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String ARTEMIS_AUTHENTICATION_TOKEN_VALUE;

    @Autowired
    private ProgrammingSubmissionTestRepository submissionRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise exercise;

    @Autowired
    private ProgrammingSubmissionAndResultIntegrationTestService testService;

    @BeforeEach
    void setUp() {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();

        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();

        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(exercise.getId()).orElseThrow();

        participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student2");

        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(exercise.getId()).orElseThrow();
    }

    @AfterEach
    void tearDown() throws Exception {
        jenkinsRequestMockProvider.reset();
        gitlabRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReceiveBuildLogsOnNewStudentParticipationResult() throws Exception {
        // Precondition: Database has participation and a programming submission.
        String userLogin = TEST_PREFIX + "student1";
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, false, ProgrammingLanguage.JAVA);
        var exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(exercise.getId()).orElseThrow();

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, userLogin);
        var submission = programmingExerciseUtilService.createProgrammingSubmission(participation, false);

        List<String> logs = new ArrayList<>();
        logs.add("[2021-05-10T15:19:49.740Z] [ERROR] BubbleSort.java:[15,9] not a statement");
        logs.add("[2021-05-10T15:19:49.740Z] [ERROR] BubbleSort.java:[15,10] ';' expected");

        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, ProgrammingLanguage.JAVA, List.of(), logs, null, new ArrayList<>());
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExtractBuildLogAnalytics_noSca() throws Exception {
        // Precondition: Database has participation and a programming submission.
        String userLogin = TEST_PREFIX + "student1";
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, false, ProgrammingLanguage.JAVA);
        ProgrammingExercise exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(exercise.getId()).orElseThrow();
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, userLogin);
        programmingExerciseUtilService.createProgrammingSubmission(participation, false);

        List<String> logs = new ArrayList<>();
        logs.add("[2021-05-10T14:58:30.000Z] Agents is getting prepared");
        logs.add("[2021-05-10T15:00:00.000Z] docker exec"); // Job started
        logs.add("[2021-05-10T15:00:05.000Z] Scanning for projects..."); // Build & test started
        logs.add("[2021-05-10T15:00:10.000Z] Dependency 1 Downloaded from");
        logs.add("[2021-05-10T15:00:15.000Z] Total time: Some time"); // Build & test finished
        logs.add("[2021-05-10T15:00:20.000Z] Everything finished"); // Job finished

        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, ProgrammingLanguage.JAVA, List.of(), logs, null, new ArrayList<>());
        postResult(notification, HttpStatus.OK);

        var statistics = buildLogStatisticsEntryRepository.findAverageBuildLogStatistics(exercise);
        assertThat(statistics.buildCount()).isEqualTo(1);
        assertThat(statistics.agentSetupDuration()).isEqualTo(90);
        assertThat(statistics.testDuration()).isEqualTo(10);
        assertThat(statistics.scaDuration()).isNull();
        assertThat(statistics.totalJobDuration()).isEqualTo(110);
        assertThat(statistics.dependenciesDownloadedCount()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExtractBuildLogAnalytics_noSca_gradle() throws Exception {
        // Precondition: Database has participation and a programming submission.
        String userLogin = TEST_PREFIX + "student1";
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, false, ProgrammingLanguage.JAVA);
        ProgrammingExercise exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(exercise.getId()).orElseThrow();
        exercise.setProjectType(ProjectType.GRADLE_GRADLE);
        programmingExerciseRepository.save(exercise);
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, userLogin);
        programmingExerciseUtilService.createProgrammingSubmission(participation, false);

        List<String> logs = new ArrayList<>();
        logs.add("[2021-05-10T15:00:00.000Z] Starting a Gradle Daemon"); // Job started
        logs.add("[2021-05-10T15:00:20.000Z] BUILD SUCCESSFUL in 20 seconds"); // Build & test started

        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, ProgrammingLanguage.JAVA, List.of(), logs, null, new ArrayList<>());
        postResult(notification, HttpStatus.OK);

        var statistics = buildLogStatisticsEntryRepository.findAverageBuildLogStatistics(exercise);
        assertThat(statistics.buildCount()).isEqualTo(1);
        assertThat(statistics.agentSetupDuration()).isNull();
        assertThat(statistics.testDuration()).isEqualTo(20);
        assertThat(statistics.scaDuration()).isNull();
        assertThat(statistics.totalJobDuration()).isEqualTo(20);
        assertThat(statistics.dependenciesDownloadedCount()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExtractBuildLogAnalytics_sca() throws Exception {
        // Precondition: Database has participation and a programming submission.
        String userLogin = TEST_PREFIX + "student1";
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, false, ProgrammingLanguage.JAVA);
        ProgrammingExercise exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(exercise.getId()).orElseThrow();
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, userLogin);
        programmingExerciseUtilService.createProgrammingSubmission(participation, false);

        List<String> logs = new ArrayList<>();
        logs.add("[2021-05-10T14:58:30.000Z] Agents is getting prepared");
        logs.add("[2021-05-10T15:00:00.000Z] docker exec"); // Job started
        logs.add("[2021-05-10T15:00:05.000Z] Scanning for projects..."); // Build & test started
        logs.add("[2021-05-10T15:00:20.000Z] Dependency 1 Downloaded from");
        logs.add("[2021-05-10T15:00:15.000Z] Total time: Some time"); // Build & test finished
        logs.add("[2021-05-10T15:00:16.000Z] Scanning for projects..."); // SCA started
        logs.add("[2021-05-10T15:00:20.000Z] Dependency 2 Downloaded from");
        logs.add("[2021-05-10T15:00:27.000Z] Total time: Some time"); // SCA finished
        logs.add("[2021-05-10T15:00:30.000Z] Everything finished"); // Job finished

        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, ProgrammingLanguage.JAVA, List.of(), logs, null, new ArrayList<>());
        postResult(notification, HttpStatus.OK);

        var statistics = buildLogStatisticsEntryRepository.findAverageBuildLogStatistics(exercise);
        assertThat(statistics.buildCount()).isEqualTo(1);
        assertThat(statistics.agentSetupDuration()).isEqualTo(90);
        assertThat(statistics.testDuration()).isEqualTo(10);
        assertThat(statistics.scaDuration()).isEqualTo(11);
        assertThat(statistics.totalJobDuration()).isEqualTo(120);
        assertThat(statistics.dependenciesDownloadedCount()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExtractBuildLogAnalytics_unsupportedProgrammingLanguage() throws Exception {
        // Precondition: Database has participation and a programming submission.
        String userLogin = TEST_PREFIX + "student1";
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, false, ProgrammingLanguage.PYTHON);
        var exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, userLogin);
        programmingExerciseUtilService.createProgrammingSubmission(participation, false);

        List<String> logs = new ArrayList<>();
        logs.add("[2021-05-10T14:58:30.000Z] Agents is getting prepared");
        logs.add("[2021-05-10T15:00:00.000Z] docker exec"); // Job started
        logs.add("[2021-05-10T15:00:05.000Z] Scanning for projects..."); // Build & test started
        logs.add("[2021-05-10T15:00:20.000Z] Dependency 1 Downloaded from");
        logs.add("[2021-05-10T15:00:15.000Z] Total time: Some time"); // Build & test finished
        logs.add("[2021-05-10T15:00:20.000Z] Everything finished"); // Job finished

        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, ProgrammingLanguage.PYTHON, List.of(), logs, null, new ArrayList<>());
        postResult(notification, HttpStatus.OK);

        var statistics = buildLogStatisticsEntryRepository.findAverageBuildLogStatistics(exercise);
        // Should not extract any statistics
        assertThat(statistics.buildCount()).isZero();
        assertThat(statistics.agentSetupDuration()).isNull();
        assertThat(statistics.testDuration()).isNull();
        assertThat(statistics.scaDuration()).isNull();
        assertThat(statistics.totalJobDuration()).isNull();
        assertThat(statistics.dependenciesDownloadedCount()).isNull();
    }

    private static Stream<Arguments> shouldSaveBuildLogsOnStudentParticipationArguments() {
        return Arrays.stream(ProgrammingLanguage.values())
                .flatMap(programmingLanguage -> Stream.of(Arguments.of(programmingLanguage, true), Arguments.of(programmingLanguage, false)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("shouldSaveBuildLogsOnStudentParticipationArguments")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestWhenPlanKeyDoesntExist(ProgrammingLanguage programmingLanguage, boolean enableStaticCodeAnalysis) throws Exception {
        // Precondition: Database has participation and a programming submission.
        String userLogin = TEST_PREFIX + "student1";
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, programmingLanguage);
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(exercise.getId()).orElseThrow();

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, userLogin);

        // Call programming-exercises/new-result which do not include build log entries yet
        var notification = createJenkinsNewResultNotification("scrambled build plan key", userLogin, programmingLanguage, List.of(), new ArrayList<>(), null, new ArrayList<>());
        postResult(notification, HttpStatus.BAD_REQUEST);

        var results = resultRepository.findAllByParticipationIdOrderByCompletionDateDesc(participation.getId());
        assertThat(results).isEmpty();
    }

    /**
     * This test results from a bug where the first push event wasn't received by Artemis but all build events.
     * This test ensures that in such a situation, the submission dates are set according to the commit dates and are therefore in the correct order.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived() throws Exception {
        testService.setUp_shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived(TEST_PREFIX);
        String userLogin = TEST_PREFIX + "student1";
        var pushJSON = new JsonParser().parse(GITLAB_PUSH_EVENT_REQUEST).getAsJsonObject();
        var firstCommitHash = pushJSON.get("before").getAsString();
        var secondCommitHash = pushJSON.get("after").getAsString();
        var firstCommitDate = ZonedDateTime.now().minusSeconds(60);
        var secondCommitDate = ZonedDateTime.now().minusSeconds(30);

        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        gitlabRequestMockProvider.mockGetPushDate(testService.participation, Map.of(firstCommitHash, firstCommitDate, secondCommitHash, secondCommitDate));

        // First commit is pushed but not recorded

        // Second commit is pushed and recorded
        postSubmission(testService.participation.getId(), HttpStatus.OK);

        // Build result for first commit is received
        var firstBuildCompleteDate = ZonedDateTime.now();
        var firstVcsDTO = new CommitDTO(firstCommitHash, uriService.getRepositorySlugFromRepositoryUri(testService.participation.getVcsRepositoryUri()), defaultBranch);
        var notificationDTOFirstCommit = createJenkinsNewResultNotification(testService.programmingExercise.getProjectKey(), userLogin, JAVA, List.of(), new ArrayList<>(),
                firstBuildCompleteDate, List.of(firstVcsDTO));

        postResult(notificationDTOFirstCommit, HttpStatus.OK);

        // Build result for second commit is received
        var secondBuildCompleteDate = ZonedDateTime.now();
        var secondVcsDTO = new CommitDTO(secondCommitHash, uriService.getRepositorySlugFromRepositoryUri(testService.participation.getVcsRepositoryUri()), defaultBranch);
        var notificationDTOSecondCommit = createJenkinsNewResultNotification(testService.programmingExercise.getProjectKey(), userLogin, JAVA, List.of(), new ArrayList<>(),
                secondBuildCompleteDate, List.of(secondVcsDTO));

        postResult(notificationDTOSecondCommit, HttpStatus.OK);

        testService.shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived(firstCommitHash, firstCommitDate, secondCommitHash, secondCommitDate);
    }

    private final List<String> logs = List.of("[2023-03-10T15:19:49.741Z] [ERROR] Log1", "[2023-03-10T15:19:49.742Z] [ERROR] Log2", "[2023-03-10T15:19:49.743Z] [ERROR] Log3");

    private static Stream<Arguments> shouldSaveBuildLogsOnStudentParticipationWithoutResultArguments() {
        return Arrays.stream(ProgrammingLanguage.values()).flatMap(programmingLanguage -> Stream.of(Arguments.of(programmingLanguage, true, true),
                Arguments.of(programmingLanguage, false, true), Arguments.of(programmingLanguage, true, false), Arguments.of(programmingLanguage, false, false)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("shouldSaveBuildLogsOnStudentParticipationWithoutResultArguments")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSaveBuildLogsOnStudentParticipationWithoutResult(ProgrammingLanguage programmingLanguage, boolean enableStaticCodeAnalysis, boolean buildFailed) throws Exception {
        // Precondition: Database has participation and a programming submission.
        String userLogin = TEST_PREFIX + "student1";
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, programmingLanguage);
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(exercise.getId()).orElseThrow();

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, userLogin);
        // It should not matter whether the submission has buildFailed set to true or false, because when the result is processed after sending it to Artemis, buildFailed is set to
        // true anyway if no feedback is provided with the result.
        var submission = programmingExerciseUtilService.createProgrammingSubmission(participation, buildFailed);

        // Call programming-exercises/new-result which does include build log entries
        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, programmingLanguage, List.of(), logs, null, new ArrayList<>());
        postResult(notification, HttpStatus.OK);

        var result = assertBuildError(participation.getId(), userLogin);
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());

        // Call again and assert that no new submissions have been created
        postResult(notification, HttpStatus.OK);
        assertNoNewSubmissions(participation.getId(), submission);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("shouldSaveBuildLogsOnStudentParticipationArguments")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSaveBuildLogsOnStudentParticipationWithoutSubmissionNorResult(ProgrammingLanguage programmingLanguage, boolean enableStaticCodeAnalysis) throws Exception {
        // Precondition: Database has participation without result and a programming
        String userLogin = TEST_PREFIX + "student1";
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, programmingLanguage);
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(exercise.getId()).orElseThrow();

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, userLogin);

        // Call programming-exercises/new-result which does include build log entries
        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, programmingLanguage, List.of(), logs, null, new ArrayList<>());
        postResult(notification, HttpStatus.OK);

        assertBuildError(participation.getId(), userLogin);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCreateGradleFeedback() throws Exception {
        String userLogin = TEST_PREFIX + "student1";
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, false, JAVA);
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise.setProjectType(ProjectType.GRADLE_GRADLE);
        exercise = programmingExerciseRepository.save(exercise);

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, userLogin);

        var notification = createJenkinsNewResultNotification(exercise.getProjectKey(), userLogin, JAVA, List.of(), List.of("test1"), logs, null, new ArrayList<>());

        var longErrorMessage = new TestCaseDetailMessageDTO("abc\nmultiline\nfeedback");
        var testCase = new TestCaseDTO("test1", "Class", 0d, new ArrayList<>(), List.of(longErrorMessage), new ArrayList<>());
        notification.getResults().get(0).testCases().set(0, testCase);

        postResult(notification, HttpStatus.OK);

        var result = resultRepository.findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDescElseThrow(participation.getId());
        // Jenkins Setup -> Gradle Feedback is not duplicated and should be kept like this
        assertThat(result.getFeedbacks().get(0).getDetailText()).isEqualTo("abc\nmultiline\nfeedback");
    }

    private Result assertBuildError(Long participationId, String userLogin) throws Exception {
        SecurityUtils.setAuthorizationObject();
        // Assert that result is linked to the participation
        var results = resultRepository.findAllByParticipationIdOrderByCompletionDateDesc(participationId);
        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getScore()).isZero();

        // Assert that the submission is linked to the participation
        var submission = (ProgrammingSubmission) result.getSubmission();
        assertThat(submission).isNotNull();
        assertThat(submission.isBuildFailed()).isTrue();

        var submissionWithLogsOptional = submissionRepository.findWithEagerBuildLogEntriesById(submission.getId());
        assertThat(submissionWithLogsOptional).isPresent();
        assertThat(submissionWithLogsOptional.get().getBuildLogEntries()).hasSize(3);

        userUtilService.changeUser(userLogin);
        // Assert that the build logs can be retrieved from the REST API from the database
        var receivedLogs = request.get("/api/repository/" + participationId + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotNull().isNotEmpty();

        return result;
    }

    /**
     * This is the simulated request from the VCS to Artemis on a new commit.
     */
    private ProgrammingSubmission postSubmission(Long participationId, HttpStatus expectedStatus) throws Exception {
        return testService.postSubmission(participationId, expectedStatus, GITLAB_PUSH_EVENT_REQUEST);
    }

    private void assertNoNewSubmissions(long participationId, ProgrammingSubmission existingSubmission) {
        var updatedSubmissions = submissionRepository.findAllByParticipationIdWithResults(participationId);
        assertThat(updatedSubmissions).hasSize(1);
        assertThat(updatedSubmissions.get(0).getId()).isEqualTo(existingSubmission.getId());
    }

    private void postResult(TestResultsDTO requestBodyMap, HttpStatus status) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        final var alteredObj = mapper.convertValue(requestBodyMap, Object.class);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", ARTEMIS_AUTHENTICATION_TOKEN_VALUE);
        request.postWithoutLocation("/api/public/programming-exercises/new-result", alteredObj, status, httpHeaders);
    }

    private TestResultsDTO createJenkinsNewResultNotification(String projectKey, String loginName, ProgrammingLanguage programmingLanguage, List<String> successfulTests,
            List<String> logs, ZonedDateTime buildRunDate, List<CommitDTO> commits) {
        return createJenkinsNewResultNotification(projectKey, loginName, programmingLanguage, successfulTests, List.of(), logs, buildRunDate, commits);
    }

    private TestResultsDTO createJenkinsNewResultNotification(String projectKey, String loginName, ProgrammingLanguage programmingLanguage, List<String> successfulTests,
            List<String> failedTests, List<String> logs, ZonedDateTime buildRunDate, List<CommitDTO> commits) {
        var repoName = (projectKey + "-" + loginName).toUpperCase();
        // The full name is specified as <FOLDER NAME> » <JOB NAME> <Build Number>
        var fullName = exercise.getProjectKey() + " » " + repoName + " #3";
        return ProgrammingExerciseFactory.generateTestResultDTO(fullName, repoName, buildRunDate, programmingLanguage, false, successfulTests, failedTests, logs, commits, null);
    }

}
