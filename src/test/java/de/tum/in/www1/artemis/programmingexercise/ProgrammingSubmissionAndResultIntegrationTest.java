package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.*;
import static de.tum.in.www1.artemis.programmingexercise.ProgrammingSubmissionConstants.*;
import static de.tum.in.www1.artemis.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.ObjectId;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildLogDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.TestConstants;
import de.tum.in.www1.artemis.web.rest.ProgrammingSubmissionResource;
import de.tum.in.www1.artemis.web.rest.ResultResource;

class ProgrammingSubmissionAndResultIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private enum IntegrationTestParticipationType {
        STUDENT, TEMPLATE, SOLUTION
    }

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

    private Long exerciseId;

    private Long templateParticipationId;

    private Long solutionParticipationId;

    private List<Long> participationIds;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();

        database.addUsers(3, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(0);
        database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        database.addStudentParticipationForProgrammingExercise(exercise, "student2");

        exerciseId = exercise.getId();
        exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(0);

        templateParticipationId = templateProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exerciseId).get().getId();
        solutionParticipationId = solutionProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exerciseId).get().getId();
        participationIds = exercise.getStudentParticipations().stream().map(Participation::getId).collect(Collectors.toList());
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        bambooRequestMockProvider.reset();
        bitbucketRequestMockProvider.reset();
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     * <p>
     * However the participation id provided by the VCS on the request is invalid.
     */
    @Test
    void shouldNotCreateSubmissionOnNotifyPushForInvalidParticipationId() throws Exception {
        long fakeParticipationId = 9999L;
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BITBUCKET_REQUEST);
        // Api should return not found.
        request.postWithoutLocation(PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + fakeParticipationId, obj, HttpStatus.NOT_FOUND, new HttpHeaders());
        // No submission should be created for the fake participation.
        assertThat(submissionRepository.findAll()).hasSize(0);
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     * However the participation id provided by the VCS on the request is invalid.
     */
    @ParameterizedTest
    @EnumSource(IntegrationTestParticipationType.class)
    void shouldCreateSubmissionOnNotifyPushForSubmission(IntegrationTestParticipationType participationType) throws Exception {
        Long participationId = getParticipationIdByType(participationType, 0);
        // set the author name to "Artemis"
        final String requestAsArtemisUser = BITBUCKET_REQUEST.replace("\"name\": \"admin\"", "\"name\": \"Artemis\"").replace("\"displayName\": \"Admin\"",
                "\"displayName\": \"Artemis\"");
        // mock request for fetchCommitInfo()
        final String projectKey = "test201904bprogrammingexercise6";
        final String slug = "test201904bprogrammingexercise6-exercise-testuser";
        final String hash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        bitbucketRequestMockProvider.mockFetchCommitInfo(projectKey, slug, hash);
        ProgrammingSubmission submission = postSubmission(participationId, HttpStatus.OK, requestAsArtemisUser);

        assertThat(submission.getParticipation().getId()).isEqualTo(participationId);
        // Needs to be set for using a custom repository method, known spring bug.
        Participation updatedParticipation = participationRepository.findWithEagerSubmissionsById(participationId).get();
        assertThat(updatedParticipation.getSubmissions().size()).isEqualTo(1);
        assertThat(updatedParticipation.getSubmissions().stream().findFirst().get().getId()).isEqualTo(submission.getId());

        // Make sure the submission has the correct commit hash.
        assertThat(submission.getCommitHash()).isEqualTo("9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d");
        // The submission should be manual and submitted.
        assertThat(submission.getType()).isEqualTo(SubmissionType.MANUAL);
        assertThat(submission.isSubmitted()).isTrue();
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     * <p>
     * Here the participation provided does exist so Artemis can create the submission.
     * <p>
     * After that the CI builds the code submission and notifies Artemis so it can create the result.
     */
    @Test
    void shouldHandleNewBuildResultCreatedByCommitWithSpecificTests() throws Exception {
        database.addCourseWithOneProgrammingExerciseAndSpecificTestCases();
        ProgrammingExercise exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(1);
        var participation = database.addStudentParticipationForProgrammingExercise(exercise, "student3");
        ProgrammingSubmission submission = postSubmission(participation.getId(), HttpStatus.OK);
        final long submissionId = submission.getId();
        postResult(participation.getBuildPlanId(), HttpStatus.OK, false);

        // Check that the result was created successfully and is linked to the participation and submission.
        List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId());
        assertThat(results).hasSize(1);
        Result createdResult = results.get(0);
        createdResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(createdResult.getId()).get();
        List<Feedback> feedbacks = createdResult.getFeedbacks();
        assertThat(feedbacks).hasSize(3);
        assertThat(createdResult.getAssessor()).isNull();
        // Needs to be set for using a custom repository method, known spring bug.
        Participation updatedParticipation = participationRepository.findWithEagerSubmissionsById(participation.getId()).get();
        submission = submissionRepository.findWithEagerResultsById(submission.getId()).get();
        assertThat(createdResult.getParticipation().getId()).isEqualTo(updatedParticipation.getId());
        assertThat(submission.getLatestResult().getId()).isEqualTo(createdResult.getId());
        assertThat(updatedParticipation.getSubmissions()).hasSize(1);
        assertThat(updatedParticipation.getSubmissions().stream().anyMatch(s -> s.getId().equals(submissionId))).isTrue();
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     * <p>
     * Here the participation provided does exist so Artemis can create the submission.
     * <p>
     * After that the CI builds the code submission and notifies Artemis so it can create the result.
     *
     * @param additionalCommit Whether an additional commit in the Assignment repo should be added to the payload
     */
    @ParameterizedTest
    @MethodSource("participationTypeAndAdditionalCommitProvider")
    void shouldHandleNewBuildResultCreatedByCommit(IntegrationTestParticipationType participationType, boolean additionalCommit) throws Exception {
        Long participationId = getParticipationIdByType(participationType, 0);
        ProgrammingSubmission submission = postSubmission(participationId, HttpStatus.OK);
        final long submissionId = submission.getId();
        postResult(participationType, 0, HttpStatus.OK, additionalCommit);

        // Check that the result was created successfully and is linked to the participation and submission.
        List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId);
        assertThat(results).hasSize(1);
        Result createdResult = results.get(0);
        // Needs to be set for using a custom repository method, known spring bug.
        Participation participation = participationRepository.findWithEagerSubmissionsById(participationId).get();
        submission = submissionRepository.findWithEagerResultsById(submission.getId()).get();
        assertThat(createdResult.getParticipation().getId()).isEqualTo(participation.getId());
        assertThat(submission.getLatestResult().getId()).isEqualTo(createdResult.getId());
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getSubmissions().stream().anyMatch(s -> s.getId().equals(submissionId))).isTrue();
    }

    private static Stream<Arguments> participationTypeAndAdditionalCommitProvider() {
        return Stream.of(Arguments.of(IntegrationTestParticipationType.STUDENT, true), Arguments.of(IntegrationTestParticipationType.STUDENT, false),
                Arguments.of(IntegrationTestParticipationType.TEMPLATE, true), Arguments.of(IntegrationTestParticipationType.TEMPLATE, false),
                Arguments.of(IntegrationTestParticipationType.SOLUTION, true), Arguments.of(IntegrationTestParticipationType.SOLUTION, false));
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     * <p>
     * After that the CI builds the code submission and notifies Artemis so it can create the result - however for an unknown reason this request is sent twice!
     * <p>
     * Only the last result should be linked to the created submission.
     */
    @ParameterizedTest
    @EnumSource(IntegrationTestParticipationType.class)
    void shouldNotLinkTwoResultsToTheSameSubmission(IntegrationTestParticipationType participationType) throws Exception {
        Long participationId = getParticipationIdByType(participationType, 0);
        // Create 1 submission.
        postSubmission(participationId, HttpStatus.OK);
        // Create 2 results for the same submission.
        postResult(participationType, 0, HttpStatus.OK, false);
        postResult(participationType, 0, HttpStatus.OK, false);

        // Make sure there are now 2 submission: 1 that was created on submit and 1 when the second result came in.
        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(2);
        ProgrammingSubmission submission1 = submissionRepository.findWithEagerResultsById(submissions.get(0).getId()).get();
        ProgrammingSubmission submission2 = submissionRepository.findWithEagerResultsById(submissions.get(1).getId()).get();

        // There should be 1 result linked to each submission.
        List<Result> results = resultRepository.findAll();
        assertThat(results).hasSize(2);
        Result result1 = resultRepository.findWithEagerSubmissionAndFeedbackById(results.get(0).getId()).get();
        Result result2 = resultRepository.findWithEagerSubmissionAndFeedbackById(results.get(1).getId()).get();
        assertThat(result1.getSubmission()).isNotNull();
        assertThat(result2.getSubmission()).isNotNull();
        assertThat(submission1.getLatestResult().getId()).isEqualTo(result1.getId());
        assertThat(submission2.getLatestResult().getId()).isEqualTo(result2.getId());
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission - however for an unknown reason this request is sent twice!
     * <p>
     * This should not create two identical submissions.
     */
    @ParameterizedTest
    @EnumSource(IntegrationTestParticipationType.class)
    void shouldNotCreateTwoSubmissionsForTwoIdenticalCommits(IntegrationTestParticipationType participationType) throws Exception {
        Long participationId = getParticipationIdByType(participationType, 0);
        // Post the same submission twice.
        ProgrammingSubmission submission = postSubmission(participationId, HttpStatus.OK);
        postSubmission(participationId, HttpStatus.OK);
        // Post the build result once.
        postResult(participationType, 0, HttpStatus.OK, false);

        // There should only be one submission and this submission should be linked to the created result.
        List<Result> results = resultRepository.findAll();
        assertThat(results).hasSize(1);
        Result result = resultRepository.findWithEagerSubmissionAndFeedbackById(results.get(0).getId()).get();
        submission = submissionRepository.findWithEagerResultsById(submission.getId()).get();
        assertThat(result.getSubmission()).isNotNull();
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());
        assertThat(submission.getLatestResult()).isNotNull();
        assertThat(submission.getLatestResult().getId()).isEqualTo(result.getId());
    }

    // TODO: write a test case that invokes notifyPush on ProgrammingSubmissionService with two identical commits. This test should then expect an IllegalStateException

    /**
     * This is the case where an instructor manually triggers the build from the CI.
     * Here no submission exists yet and now needs to be created on the result notification.
     */
    @ParameterizedTest
    @EnumSource(IntegrationTestParticipationType.class)
    void shouldCreateSubmissionForManualBuildRun(IntegrationTestParticipationType participationType) throws Exception {
        Long participationId = getParticipationIdByType(participationType, 0);
        postResult(participationType, 0, HttpStatus.OK, false);

        Participation participation = participationRepository.findWithEagerSubmissionsById(participationId).get();

        // Now a submission for the manual build should exist.
        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        List<Result> results = resultRepository.findAll();
        assertThat(submissions).hasSize(1);
        ProgrammingSubmission submission = submissions.get(0);
        assertThat(results).hasSize(1);
        Result result = results.get(0);
        assertThat(submission.getCommitHash()).isEqualTo("9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d");
        // The submission should be other as it was not created by a commit.
        assertThat(submission.getType()).isEqualTo(SubmissionType.OTHER);
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());
        assertThat(participation.getSubmissions().size()).isEqualTo(1);
    }

    /**
     * This is the case where an instructor manually triggers the build from the CI.
     * Here no submission exists yet and now needs to be created on the result notification.
     */
    @ParameterizedTest
    @EnumSource(IntegrationTestParticipationType.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldTriggerManualBuildRunForLastCommit(IntegrationTestParticipationType participationType) throws Exception {
        Long participationId = getParticipationIdByType(participationType, 0);
        final var programmingParticipation = (ProgrammingExerciseParticipation) participationRepository.findById(participationId).get();
        bambooRequestMockProvider.mockTriggerBuild(programmingParticipation);
        var repositoryUrl = (programmingParticipation).getVcsRepositoryUrl();
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(repositoryUrl);
        triggerBuild(participationType, 0);

        // Now a submission for the manual build should exist.
        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);
        ProgrammingSubmission submission = submissions.get(0);
        assertThat(submission.getCommitHash()).isEqualTo(COMMIT_HASH_OBJECT_ID.getName());
        assertThat(submission.getType()).isEqualTo(SubmissionType.MANUAL);
        assertThat(submission.isSubmitted()).isTrue();

        Participation participation = participationRepository.findWithEagerSubmissionsById(participationId).get();

        postResult(participationType, 0, HttpStatus.OK, false);

        // The new result should be attached to the created submission, no new submission should have been created.
        submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);
        List<Result> results = resultRepository.findAll();
        assertThat(results).hasSize(1);
        Result result = results.get(0);
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());
        assertThat(participation.getSubmissions().size()).isEqualTo(1);
    }

    /**
     * This is the case where an instructor manually triggers the build from the CI.
     * Here no submission exists yet and now needs to be created on the result notification.
     */
    @ParameterizedTest
    @EnumSource(IntegrationTestParticipationType.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldTriggerInstructorBuildRunForLastCommit(IntegrationTestParticipationType participationType) throws Exception {
        // Set buildAndTestAfterDueDate in future.
        setBuildAndTestAfterDueDateForProgrammingExercise(ZonedDateTime.now().plusDays(1));
        Long participationId = getParticipationIdByType(participationType, 0);
        final var programmingParticipation = (ProgrammingExerciseParticipation) participationRepository.findById(participationId).get();
        bambooRequestMockProvider.mockTriggerBuild(programmingParticipation);
        var repositoryUrl = programmingParticipation.getVcsRepositoryUrl();
        ObjectId objectId = COMMIT_HASH_OBJECT_ID;
        doReturn(objectId).when(gitService).getLastCommitHash(repositoryUrl);
        triggerInstructorBuild(participationType, 0);

        // Now a submission for the manual build should exist.
        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);
        ProgrammingSubmission submission = submissions.get(0);
        assertThat(submission.getCommitHash()).isEqualTo(objectId.getName());
        assertThat(submission.getType()).isEqualTo(SubmissionType.INSTRUCTOR);
        assertThat(submission.isSubmitted()).isTrue();

        Participation participation = participationRepository.findWithEagerSubmissionsById(participationId).get();

        postResult(participationType, 0, HttpStatus.OK, false);

        // The new result should be attached to the created submission, no new submission should have been created.
        submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);
        List<Result> results = resultRepository.findAll();
        assertThat(results).hasSize(1);
        Result result = results.get(0);
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());
        assertThat(participation.getSubmissions().size()).isEqualTo(1);
        assertThat(result.isRated()).isTrue();
    }

    /**
     * After a commit into the test repository, the VCS triggers Artemis to create submissions for all participations of the given exercise.
     * The reason for this is that the test repository update will trigger a build run in the CI for every participation.
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldCreateSubmissionsForAllParticipationsOfExerciseAfterTestRepositoryCommit() throws Exception {
        final var templateParticipation = templateProgrammingExerciseParticipationRepository.findById(templateParticipationId).get();
        bambooRequestMockProvider.mockTriggerBuild(templateParticipation);
        setBuildAndTestAfterDueDateForProgrammingExercise(null);
        // Phase 1: There has been a commit to the test repository, the VCS now informs Artemis about it.
        postTestRepositorySubmission();
        // There are two student participations, so after the test notification two new submissions should have been created.
        List<Participation> participations = new ArrayList<>();
        participations.add(participationRepository.findWithEagerSubmissionsById(solutionParticipationId).get());
        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        // We only create submissions for the solution participation after a push to the test repository.
        assertThat(submissions).hasSize(1);
        for (Participation participation : participations) {
            assertThat(submissions.stream().filter(s -> s.getParticipation().getId().equals(participation.getId())).collect(Collectors.toList())).hasSize(1);
        }
        assertThat(submissions.stream().allMatch(s -> s.isSubmitted() && s.getCommitHash().equals(TEST_COMMIT) && s.getType().equals(SubmissionType.TEST))).isTrue();

        // Phase 2: Now the CI informs Artemis about the participation build results.
        postResult(IntegrationTestParticipationType.SOLUTION, 0, HttpStatus.OK, false);
        // The number of total participations should not have changed.
        assertThat(participationRepository.count()).isEqualTo(4);
        // Now for both student's submission a result should have been created and assigned to the submission.
        List<Result> results = resultRepository.findAll();
        submissions = submissionRepository.findAll();
        participations = new LinkedList<>();
        participations.add(solutionProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exerciseId).get());
        // After a push to the test repository, only the solution and template repository are built.
        assertThat(results).hasSize(1);
        for (Result r : results) {
            boolean hasMatchingSubmission = submissions.stream().anyMatch(s -> s.getId().equals(r.getSubmission().getId()));
            assertThat(hasMatchingSubmission);
        }
        for (Participation p : participations) {
            assertThat(p.getSubmissions()).hasSize(1);
            assertThat(p.getResults()).hasSize(1);
            Result participationResult = new ArrayList<>(p.getResults()).get(0);
            Result submissionResult = new ArrayList<>(p.getSubmissions()).get(0).getLatestResult();
            assertThat(participationResult.getId()).isEqualTo(submissionResult.getId());
            // Submissions with type TEST and no buildAndTestAfterDueDate should be rated.
            assertThat(participationResult.isRated()).isTrue();
        }
    }

    private static Stream<Arguments> shouldSavebuildLogsOnStudentParticipationArguments() {
        return Arrays.stream(ProgrammingLanguage.values())
                .flatMap(programmingLanguage -> Stream.of(Arguments.of(programmingLanguage, true), Arguments.of(programmingLanguage, false)));
    }

    @ParameterizedTest
    @MethodSource("shouldSavebuildLogsOnStudentParticipationArguments")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldSaveBuildLogsOnStudentParticipationWithoutResult(ProgrammingLanguage programmingLanguage, boolean enableStaticCodeAnalysis) throws Exception {
        database.addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, programmingLanguage);
        ProgrammingExercise exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(1);

        // Precondition: Database has participation without result and a programming submission.
        var participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        var submission = ModelFactory.generateProgrammingSubmission(true);
        submission.setCommitHash(TestConstants.COMMIT_HASH_STRING);
        submission.setType(SubmissionType.MANUAL);
        database.addProgrammingSubmission(exercise, submission, "student1");

        // Call programming-exercises/new-result which include build log entries
        var buildLog = new BambooBuildLogDTO();
        buildLog.setLog("COMPILATION ERROR missing something");
        buildLog.setDate(ZonedDateTime.now().minusMinutes(1));
        buildLog.setUnstyledLog("COMPILATION ERROR missing something");
        postResultWithBuildLogs(participation.getBuildPlanId(), List.of(buildLog), HttpStatus.OK, true);

        // Assert that result has been created and is linked to the participation and submission
        var result = resultRepository.findDistinctBySubmissionId(submission.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getParticipation().getId()).isEqualTo(participation.getId());
        assertThat(result.get().getSubmission().getId()).isEqualTo(submission.getId());

        // Assert that the submission in the database contains the build log entries
        var updatedSubmission = submissionRepository.findWithEagerBuildLogEntriesById(submission.getId());
        assertThat(updatedSubmission).isPresent();
        assertThat(updatedSubmission.get().getBuildLogEntries().size()).isGreaterThan(0);
        updatedSubmission.get().getBuildLogEntries().forEach(buildLogEntry -> assertThat(buildLogEntry.getLog()).isEqualTo(buildLog.getLog()));

        // Assert that build logs have been saved in the build log repository.
        var savedLogs = buildLogEntryRepository.findAll();
        assertThat(savedLogs.size()).isGreaterThan(0);
        assertThat(savedLogs.get(0).getProgrammingSubmission().getId()).isEqualTo(submission.getId());

        // TODO: This doesn't work because of this exception: java.lang.IllegalArgumentException: userAuthorities cannot be null
        /*
         * var receivedLogs = request.get("/api/repository/" + participation.getId() + "/buildlogs", HttpStatus.OK, List.class); assertThat(receivedLogs.size()).isGreaterThan(0);
         * receivedLogs.forEach(receivedLog -> { BuildLogEntry receivedBuildLogEntry = (BuildLogEntry) receivedLog;
         * assertThat(receivedBuildLogEntry.getProgrammingSubmission().getId()).isEqualTo(submission.getId()); });
         */
    }

    @ParameterizedTest
    @MethodSource("shouldSavebuildLogsOnStudentParticipationArguments")
    void shouldSaveBuildLogsOnStudentParticipationWithoutSubmissionNorResult(ProgrammingLanguage programmingLanguage, boolean enableStaticCodeAnalysis) throws Exception {
        database.addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, programmingLanguage);
        ProgrammingExercise exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(1);

        // Precondition: Database has participation without result and a programming submission.
        var participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");

        // Call programming-exercises/new-result which includes build log entries
        var buildLog = new BambooBuildLogDTO();
        buildLog.setLog("COMPILATION ERROR missing something");
        buildLog.setDate(ZonedDateTime.now().minusMinutes(1));
        buildLog.setUnstyledLog("COMPILATION ERROR missing something");
        postResultWithBuildLogs(participation.getBuildPlanId(), List.of(buildLog), HttpStatus.OK, true);

        // Assert that result linked to the participation
        var result = resultRepository.findAllByParticipationIdOrderByCompletionDateDesc(participation.getId());
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).getParticipation().getId()).isEqualTo(participation.getId());

        // Assert that the created submission linked to the participation
        var submissions = submissionRepository.findAll();
        assertThat(submissions.size()).isEqualTo(1);
        assertThat(submissions.get(0).getParticipation().getId()).isEqualTo(participation.getId());

        // Assert that created submission is linked to the result
        assertThat(result.get(0).getSubmission().getId()).isEqualTo(submissions.get(0).getId());

        // Assert that the submission in the database contains the build log entries
        var submissionWithBuildLogEntries = submissionRepository.findWithEagerBuildLogEntriesById(submissions.get(0).getId());
        assertThat(submissionWithBuildLogEntries).isPresent();
        assertThat(submissionWithBuildLogEntries.get().getBuildLogEntries().size()).isGreaterThan(0);
        submissionWithBuildLogEntries.get().getBuildLogEntries().forEach(buildLogEntry -> assertThat(buildLogEntry.getLog()).isEqualTo(buildLog.getLog()));

        // Assert that build logs have been saved in the build log repository.
        var savedLogs = buildLogEntryRepository.findAll();
        assertThat(savedLogs.size()).isGreaterThan(0);
        assertThat(savedLogs.get(0).getProgrammingSubmission().getId()).isEqualTo(submissions.get(0).getId());
    }

    /**
     * This is the simulated request from the VCS to Artemis on a new commit.
     */
    private ProgrammingSubmission postSubmission(Long participationId, HttpStatus expectedStatus) throws Exception {
        return postSubmission(participationId, expectedStatus, BITBUCKET_REQUEST);
    }

    /**
     * This is the simulated request from the VCS to Artemis on a new commit.
     */
    private ProgrammingSubmission postSubmission(Long participationId, HttpStatus expectedStatus, String jsonRequest) throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(jsonRequest);

        // Api should return ok.
        request.postWithoutLocation(PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + participationId, obj, expectedStatus, new HttpHeaders());

        // Submission should have been created for the participation.
        assertThat(submissionRepository.findAll()).hasSize(1);
        // Make sure that both the submission and participation are correctly linked with each other.
        return submissionRepository.findAll().get(0);
    }

    /**
     * Simulate a commit to the test repository, this executes a http request from the VCS to Artemis.
     */
    @SuppressWarnings("unchecked")
    private void postTestRepositorySubmission() throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BITBUCKET_REQUEST);

        Map<String, Object> requestBodyMap = (Map<String, Object>) obj;
        List<Map<String, Object>> changes = (List<Map<String, Object>>) requestBodyMap.get("changes");
        changes.get(0).put("toHash", TEST_COMMIT);

        // Api should return ok.
        request.postWithoutLocation(TEST_CASE_CHANGED_API_PATH + exerciseId, obj, HttpStatus.OK, new HttpHeaders());
    }

    private String getBuildPlanIdByParticipationType(IntegrationTestParticipationType participationType, int participationNumber) {
        return switch (participationType) {
            case TEMPLATE -> "BASE";
            case SOLUTION -> "SOLUTION";
            default -> getStudentLoginFromParticipation(participationNumber);
        };
    }

    private void triggerBuild(IntegrationTestParticipationType participationType, int participationNumber) throws Exception {
        Long id = getParticipationIdByType(participationType, participationNumber);
        request.postWithoutLocation("/api/programming-submissions/" + id + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
    }

    private void triggerInstructorBuild(IntegrationTestParticipationType participationType, int participationNumber) throws Exception {
        Long id = getParticipationIdByType(participationType, participationNumber);
        request.postWithoutLocation("/api/programming-submissions/" + id + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.OK, new HttpHeaders());
    }

    /**
     * This is the simulated request from the CI to Artemis on a new build result.
     */
    private void postResult(IntegrationTestParticipationType participationType, int participationNumber, HttpStatus expectedStatus, boolean additionalCommit) throws Exception {
        String buildPlanStudentId = getBuildPlanIdByParticipationType(participationType, participationNumber);
        postResult(exercise.getProjectKey().toUpperCase() + "-" + buildPlanStudentId, expectedStatus, additionalCommit);
    }

    private void postResultWithBuildLogs(String participationId, List<BambooBuildLogDTO> buildLogs, HttpStatus expectedStatus, boolean additionalCommit) throws Exception {
        final var requestBodyMap = createBambooBuildResultNotificationDTO();
        requestBodyMap.getBuild().getJobs().forEach(job -> job.setLogs(buildLogs));
        postResult(participationId, requestBodyMap, expectedStatus, additionalCommit);
    }

    private void postResult(String participationId, HttpStatus expectedStatus, boolean additionalCommit) throws Exception {
        final var requestBodyMap = createBambooBuildResultNotificationDTO();
        postResult(participationId, requestBodyMap, expectedStatus, additionalCommit);
    }

    private void postResult(String participationId, BambooBuildResultNotificationDTO requestBodyMap, HttpStatus expectedStatus, boolean additionalCommit) throws Exception {
        requestBodyMap.getPlan().setKey(participationId.toUpperCase());
        if (additionalCommit) {
            var newCommit = new BambooBuildResultNotificationDTO.BambooCommitDTO();
            newCommit.setComment("Some commit that occurred before");
            newCommit.setId("90b6af5650c30d35a0836fd58c677f8980e1df27");
            requestBodyMap.getBuild().getVcs().get(0).getCommits().add(newCommit);
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        final var alteredObj = mapper.convertValue(requestBodyMap, Object.class);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", ARTEMIS_AUTHENTICATION_TOKEN_VALUE);
        request.postWithoutLocation("/api" + NEW_RESULT_RESOURCE_PATH, alteredObj, expectedStatus, httpHeaders);
    }

    private BambooBuildResultNotificationDTO createBambooBuildResultNotificationDTO() throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BAMBOO_REQUEST);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper.convertValue(obj, BambooBuildResultNotificationDTO.class);
    }

    private String getStudentLoginFromParticipation(int participationNumber) {
        StudentParticipation participation = studentParticipationRepository.findWithStudentById(participationIds.get(participationNumber)).get();
        return participation.getParticipantIdentifier();
    }

    private Long getParticipationIdByType(IntegrationTestParticipationType participationType, int participationNumber) {
        return switch (participationType) {
            case SOLUTION -> solutionParticipationId;
            case TEMPLATE -> templateParticipationId;
            default -> participationIds.get(participationNumber);
        };
    }

    private void setBuildAndTestAfterDueDateForProgrammingExercise(ZonedDateTime buildAndTestAfterDueDate) {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exerciseId).get();
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(buildAndTestAfterDueDate);
        programmingExerciseRepository.save(programmingExercise);
    }
}
