package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.*;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.C;
import static de.tum.in.www1.artemis.programmingexercise.ProgrammingSubmissionConstants.*;
import static de.tum.in.www1.artemis.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.eclipse.jgit.lib.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
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
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildLogDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.util.ModelFactory;

class ProgrammingSubmissionAndResultBitbucketBambooIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private enum IntegrationTestParticipationType {
        STUDENT, TEMPLATE, SOLUTION
    }

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String ARTEMIS_AUTHENTICATION_TOKEN_VALUE;

    @Autowired
    private ProgrammingSubmissionRepository submissionRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Autowired
    private TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ExamDateService examDateService;

    @Autowired
    private ProgrammingSubmissionAndResultIntegrationTestService testService;

    private Long exerciseId;

    private Long templateParticipationId;

    private Long solutionParticipationId;

    private List<Long> participationIds;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();

        database.addUsers(3, 2, 0, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndLegalSubmissions().get(0);
        database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        database.addStudentParticipationForProgrammingExercise(exercise, "student2");

        exerciseId = exercise.getId();
        exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndLegalSubmissions().get(0);

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
    @WithMockUser(username = "student1", roles = "USER")
    void shouldNotCreateSubmissionOnNotifyPushForInvalidParticipationId() throws Exception {
        long fakeParticipationId = 9999L;
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BITBUCKET_PUSH_EVENT_REQUEST);
        // Api should return not found.
        request.postWithoutLocation(PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + fakeParticipationId, obj, HttpStatus.NOT_FOUND, new HttpHeaders());
        // No submission should be created for the fake participation.
        assertThat(submissionRepository.findAll()).isEmpty();
    }

    private ProgrammingSubmission mockCommitInfoAndPostSubmission(long participationId) throws Exception {
        // set the author name to "Artemis"
        final String requestAsArtemisUser = BITBUCKET_PUSH_EVENT_REQUEST.replace("\"name\": \"admin\"", "\"name\": \"Artemis\"").replace("\"displayName\": \"Admin\"",
                "\"displayName\": \"Artemis\"");
        // mock request for fetchCommitInfo()
        final String projectKey = "test201904bprogrammingexercise6";
        final String slug = "test201904bprogrammingexercise6-exercise-testuser";
        final String hash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        bitbucketRequestMockProvider.mockFetchCommitInfo(projectKey, slug, hash);
        ProgrammingExercise programmingExercise = (ProgrammingExercise) participationRepository.findById(participationId).orElseThrow().getExercise();
        bitbucketRequestMockProvider.mockGetDefaultBranch(defaultBranch, programmingExercise.getProjectKey());
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(programmingExercise);
        bitbucketRequestMockProvider.mockGetPushDate(programmingExercise.getProjectKey(), hash, ZonedDateTime.now());
        bitbucketRequestMockProvider.mockPutDefaultBranch(programmingExercise.getProjectKey());
        return testService.postSubmission(participationId, HttpStatus.OK, requestAsArtemisUser);
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     * However the participation id provided by the VCS on the request is invalid.
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(IntegrationTestParticipationType.class)
    @WithMockUser(username = "student1", roles = "USER")
    void shouldCreateSubmissionOnNotifyPushForSubmission(IntegrationTestParticipationType participationType) throws Exception {
        String commitHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        Long participationId = getParticipationIdByType(participationType, 0);
        // set the author name to "Artemis"
        ProgrammingSubmission submission = mockCommitInfoAndPostSubmission(participationId);

        assertThat(submission.getParticipation().getId()).isEqualTo(participationId);
        // Needs to be set for using a custom repository method, known spring bug.
        Participation updatedParticipation = participationRepository.findWithEagerLegalSubmissionsById(participationId).get();
        assertThat(updatedParticipation.getSubmissions()).hasSize(1);
        assertThat(updatedParticipation.getSubmissions().stream().findFirst().get().getId()).isEqualTo(submission.getId());

        // Make sure the submission has the correct commit hash.
        assertThat(submission.getCommitHash()).isEqualTo(commitHash);
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
    @WithMockUser(username = "student1", roles = "USER")
    void shouldHandleNewBuildResultCreatedByCommitWithSpecificTests() throws Exception {
        database.addCourseWithOneProgrammingExerciseAndSpecificTestCases();
        ProgrammingExercise exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndLegalSubmissions().get(1);
        bitbucketRequestMockProvider.mockGetDefaultBranch(defaultBranch, exercise.getProjectKey());
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(exercise);
        bitbucketRequestMockProvider.mockGetPushDate(exercise.getProjectKey(), "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d", ZonedDateTime.now());
        bitbucketRequestMockProvider.mockPutDefaultBranch(exercise.getProjectKey());
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
        Participation updatedParticipation = participationRepository.findWithEagerLegalSubmissionsById(participation.getId()).get();
        submission = submissionRepository.findWithEagerResultsById(submission.getId()).get();
        assertThat(createdResult.getParticipation().getId()).isEqualTo(updatedParticipation.getId());
        assertThat(submission.getLatestResult().getId()).isEqualTo(createdResult.getId());
        assertThat(updatedParticipation.getSubmissions()).hasSize(1);
        assertThat(updatedParticipation.getSubmissions().stream().anyMatch(s -> s.getId().equals(submissionId))).isTrue();

        // Do a call to new-result again and assert that no new submission is created.
        postResult(participation.getBuildPlanId(), HttpStatus.OK, false);
        assertNoNewSubmissionsAndIsSubmission(submission);
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
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("participationTypeAndAdditionalCommitProvider")
    @WithMockUser(username = "student1", roles = "USER")
    void shouldHandleNewBuildResultCreatedByCommit(IntegrationTestParticipationType participationType, boolean additionalCommit) throws Exception {
        bitbucketRequestMockProvider.mockGetDefaultBranch(defaultBranch, exercise.getProjectKey());
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(exercise);
        bitbucketRequestMockProvider.mockGetPushDate(exercise.getProjectKey(), "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d", ZonedDateTime.now());
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, exercise.getProjectKey());

        Long participationId = getParticipationIdByType(participationType, 0);
        ProgrammingSubmission submission = postSubmission(participationId, HttpStatus.OK);
        final long submissionId = submission.getId();
        postResult(participationType, 0, HttpStatus.OK, additionalCommit);

        // Check that the result was created successfully and is linked to the participation and submission.
        List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId);
        assertThat(results).hasSize(1);
        Result createdResult = results.get(0);
        // Needs to be set for using a custom repository method, known spring bug.
        Participation participation = participationRepository.findWithEagerLegalSubmissionsById(participationId).get();
        submission = submissionRepository.findWithEagerResultsById(submission.getId()).get();
        assertThat(createdResult.getParticipation().getId()).isEqualTo(participation.getId());
        assertThat(submission.getLatestResult().getId()).isEqualTo(createdResult.getId());
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getSubmissions().stream().anyMatch(s -> s.getId().equals(submissionId))).isTrue();

        // Do a call to new-result again and assert that no new submission is created.
        postResult(participationType, 0, HttpStatus.OK, additionalCommit);
        assertNoNewSubmissionsAndIsSubmission(submission);
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
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(IntegrationTestParticipationType.class)
    @WithMockUser(username = "student1", roles = "USER")
    void shouldNotLinkTwoResultsToTheSameSubmission(IntegrationTestParticipationType participationType) throws Exception {
        bitbucketRequestMockProvider.mockGetDefaultBranch(defaultBranch, exercise.getProjectKey());
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(exercise);
        bitbucketRequestMockProvider.mockGetPushDate(exercise.getProjectKey(), "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d", ZonedDateTime.now());
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, exercise.getProjectKey());

        Long participationId = getParticipationIdByType(participationType, 0);
        // Create 1 submission.
        var submission = postSubmission(participationId, HttpStatus.OK);
        // Create 2 results for the same submission.
        postResult(participationType, 0, HttpStatus.OK, false);
        postResult(participationType, 0, HttpStatus.OK, false);

        // Make sure there's only 1 submission
        assertNoNewSubmissionsAndIsSubmission(submission);

        // The results should be linked to the submission.
        List<Result> results = resultRepository.findAll();
        assertThat(results).hasSize(2);
        results.forEach(result -> {
            var resultWithSubmission = resultRepository.findWithEagerSubmissionAndFeedbackById(result.getId());
            assertThat(resultWithSubmission).isPresent();
            assertThat(resultWithSubmission.get().getSubmission()).isNotNull();
            assertThat(resultWithSubmission.get().getSubmission().getId()).isEqualTo(submission.getId());
        });

        // Make sure the latest result is the last result
        var latestSubmissionOrEmpty = submissionRepository.findWithEagerResultsById(submission.getId());
        assertThat(latestSubmissionOrEmpty).isPresent();
        var latestSubmission = latestSubmissionOrEmpty.get();
        var latestResult = latestSubmission.getLatestResult();
        assertThat(latestResult).isNotNull();
        assertThat(latestResult.getId()).isEqualTo(results.get(1).getId());
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission - however for an unknown reason this request is sent twice!
     * <p>
     * This should not create two identical submissions.
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(IntegrationTestParticipationType.class)
    @WithMockUser(username = "student1", roles = "USER")
    void shouldNotCreateTwoSubmissionsForTwoIdenticalCommits(IntegrationTestParticipationType participationType) throws Exception {
        bitbucketRequestMockProvider.mockGetDefaultBranch(defaultBranch, exercise.getProjectKey());
        bitbucketRequestMockProvider.mockGetPushDate(exercise.getProjectKey(), "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d", ZonedDateTime.now());
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(exercise);

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

        // Do another call to new-result again and assert that no new submission is created.
        postResult(participationType, 0, HttpStatus.OK, false);
        assertNoNewSubmissionsAndIsSubmission(submission);
    }

    // TODO: write a test case that invokes notifyPush on ProgrammingSubmissionService with two identical commits. This test should then expect an IllegalStateException

    /**
     * This is the case where an instructor manually triggers the build from the CI.
     * Here no submission exists yet and now needs to be created on the result notification.
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(IntegrationTestParticipationType.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldCreateSubmissionForManualBuildRun(IntegrationTestParticipationType participationType) throws Exception {
        bitbucketRequestMockProvider.mockGetPushDate(exercise.getProjectKey(), "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d", ZonedDateTime.now());
        Long participationId = getParticipationIdByType(participationType, 0);
        postResult(participationType, 0, HttpStatus.OK, false);

        Participation participation = participationRepository.findWithEagerLegalSubmissionsById(participationId).get();

        // Now a submission for the manual build should exist.
        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        List<Result> results = resultRepository.findAll();
        assertThat(submissions).hasSize(1);
        ProgrammingSubmission submission = submissions.get(0);
        assertThat(results).hasSize(1);
        Result result = results.get(0);
        assertThat(submission.getCommitHash()).isEqualTo("9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d");
        // The submission should be other as it was not created by a commit.
        assertThat(submission.getType()).isEqualTo(SubmissionType.MANUAL);
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());
        assertThat(participation.getSubmissions()).hasSize(1);

        postResult(participationType, 0, HttpStatus.OK, false);
        assertNoNewSubmissionsAndIsSubmission(submission);
    }

    /**
     * This is the case where an instructor manually triggers the build from the CI.
     * Here no submission exists yet and now needs to be created on the result notification.
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
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

        Participation participation = participationRepository.findWithEagerLegalSubmissionsById(participationId).get();

        postResult(participationType, 0, HttpStatus.OK, false);

        // The new result should be attached to the created submission, no new submission should have been created.
        submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);
        List<Result> results = resultRepository.findAll();
        assertThat(results).hasSize(1);
        Result result = results.get(0);
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());
        assertThat(participation.getSubmissions()).hasSize(1);

        // Do another call to new-result again and assert that no new submission is created.
        postResult(participationType, 0, HttpStatus.OK, false);
        assertNoNewSubmissionsAndIsSubmission(submission);
    }

    /**
     * This is the case where an instructor manually triggers the build from the CI.
     * Here no submission exists yet and now needs to be created on the result notification.
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
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

        Participation participation = participationRepository.findWithEagerLegalSubmissionsById(participationId).get();

        postResult(participationType, 0, HttpStatus.OK, false);

        // The new result should be attached to the created submission, no new submission should have been created.
        submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);
        List<Result> results = resultRepository.findAll();
        assertThat(results).hasSize(1);
        Result result = results.get(0);
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(result.isRated()).isTrue();

        // Do another call to new-result again and assert that no new submission is created.
        postResult(participationType, 0, HttpStatus.OK, false);
        assertNoNewSubmissionsAndIsSubmission(submission);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCaseChanged() throws Exception {
        final var templateParticipation = templateProgrammingExerciseParticipationRepository.findById(templateParticipationId).get();
        bambooRequestMockProvider.mockTriggerBuild(templateParticipation);
        setBuildAndTestAfterDueDateForProgrammingExercise(null);
        postTestRepositorySubmissionWithoutCommit(HttpStatus.INTERNAL_SERVER_ERROR);
        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        when(gitService.getLastCommitHash(any())).thenReturn(ObjectId.fromString(dummyHash));
        postTestRepositorySubmissionWithoutCommit(HttpStatus.OK);
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
        participations.add(participationRepository.findWithEagerLegalSubmissionsById(solutionParticipationId).get());
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
        participations = new ArrayList<>();
        participations.add(solutionProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exerciseId).get());
        // After a push to the test repository, only the solution and template repository are built.
        assertThat(results).hasSize(1);
        for (Result r : results) {
            boolean hasMatchingSubmission = submissions.stream().anyMatch(s -> s.getId().equals(r.getSubmission().getId()));
            assertThat(hasMatchingSubmission).isTrue();
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

    /**
     * This test results from a bug where the first push event wasn't received by Artemis but all build events.
     * This test ensures that in such a situation, the submission dates are set according to the commit dates and are therefore in the correct order.
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived() throws Exception {
        testService.setUp_shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived();
        var pushJSON = (JSONObject) new JSONParser().parse(BITBUCKET_PUSH_EVENT_REQUEST);
        var changes = (JSONArray) pushJSON.get("changes");
        var firstChange = (JSONObject) changes.get(0);
        var firstCommitHash = (String) firstChange.get("fromHash");
        var secondCommitHash = (String) firstChange.get("toHash");
        var secondCommitDate = ZonedDateTime.parse(pushJSON.get("date").toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"));
        var firstCommitDate = secondCommitDate.minusSeconds(30);

        bitbucketRequestMockProvider.mockGetDefaultBranch(defaultBranch, testService.programmingExercise.getProjectKey());
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(testService.programmingExercise);
        bitbucketRequestMockProvider.mockGetPushDate(testService.programmingExercise.getProjectKey(), firstCommitHash, firstCommitDate);

        // First commit is pushed but not recorded
        var firstCommit = new BambooBuildResultNotificationDTO.BambooCommitDTO();
        firstCommit.setId(firstCommitHash);
        firstCommit.setComment("First commit");

        // Second commit is pushed and recorded
        var secondCommit = new BambooBuildResultNotificationDTO.BambooCommitDTO();
        secondCommit.setId(secondCommitHash);
        secondCommit.setComment("Second commit");
        postSubmission(testService.participation.getId(), HttpStatus.OK);

        // Build result for first commit is received
        var firstBuildCompleteDate = ZonedDateTime.now();
        var firstVcsDTO = new BambooBuildResultNotificationDTO.BambooVCSDTO();
        firstVcsDTO.setId(firstCommit.getId());
        firstVcsDTO.setRepositoryName(ASSIGNMENT_REPO_NAME);
        firstVcsDTO.setCommits(List.of(firstCommit));
        var notificationDTOFirstCommit = ModelFactory.generateBambooBuildResultWithLogs(ASSIGNMENT_REPO_NAME, List.of(), List.of());
        notificationDTOFirstCommit.getBuild().setBuildCompletedDate(firstBuildCompleteDate);
        notificationDTOFirstCommit.getBuild().setVcs(List.of(firstVcsDTO));

        postResult(testService.participation.getBuildPlanId(), notificationDTOFirstCommit, HttpStatus.OK, false);

        // Build result for second commit is received
        var secondBuildCompleteDate = ZonedDateTime.now();
        var secondVcsDTO = new BambooBuildResultNotificationDTO.BambooVCSDTO();
        secondVcsDTO.setId(secondCommit.getId());
        secondVcsDTO.setRepositoryName(ASSIGNMENT_REPO_NAME);
        secondVcsDTO.setCommits(List.of(firstCommit, secondCommit));
        var notificationDTOSecondCommit = ModelFactory.generateBambooBuildResultWithLogs(ASSIGNMENT_REPO_NAME, List.of(), List.of());
        notificationDTOSecondCommit.getBuild().setBuildCompletedDate(secondBuildCompleteDate);
        notificationDTOSecondCommit.getBuild().setVcs(List.of(secondVcsDTO));

        postResult(testService.participation.getBuildPlanId(), notificationDTOSecondCommit, HttpStatus.OK, false);

        testService.shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived(firstCommitHash, firstCommitDate, secondCommitHash, secondCommitDate);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("shouldSavebuildLogsOnStudentParticipationArguments")
    @WithMockUser(username = "student1", roles = "USER")
    void shouldSaveBuildLogsOnStudentParticipationWithoutResult(ProgrammingLanguage programmingLanguage, boolean enableStaticCodeAnalysis) throws Exception {
        // Precondition: Database has participation and a programming submission but no result.
        String userLogin = "student1";
        database.addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, programmingLanguage);
        ProgrammingExercise exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndLegalSubmissions().get(1);
        var participation = database.addStudentParticipationForProgrammingExercise(exercise, userLogin);
        var submission = database.createProgrammingSubmission(participation, false);

        // Call programming-exercises/new-result which includes build log entries
        var buildLog = new BambooBuildLogDTO();
        buildLog.setLog("[ERROR] COMPILATION ERROR missing something");
        buildLog.setDate(ZonedDateTime.now().minusMinutes(1));
        buildLog.setUnstyledLog("[ERROR] COMPILATION ERROR missing something");
        postResultWithBuildLogs(participation.getBuildPlanId(), HttpStatus.OK, false);

        var result = assertBuildError(participation.getId(), userLogin, programmingLanguage);
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());

        // Do another call to new-result again and assert that no new submission is created.
        postResultWithBuildLogs(participation.getBuildPlanId(), HttpStatus.OK, false);
        assertNoNewSubmissionsAndIsSubmission(submission);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("shouldSavebuildLogsOnStudentParticipationArguments")
    @WithMockUser(username = "student1", roles = "USER")
    void shouldSaveBuildLogsOnStudentParticipationWithoutSubmissionNorResult(ProgrammingLanguage programmingLanguage, boolean enableStaticCodeAnalysis) throws Exception {
        // Precondition: Database has participation without result and a programming submission.
        String userLogin = "student1";
        database.addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, programmingLanguage);
        ProgrammingExercise exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndLegalSubmissions().get(1);
        bitbucketRequestMockProvider.mockGetPushDate(exercise.getProjectKey(), "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d", ZonedDateTime.now());
        var participation = database.addStudentParticipationForProgrammingExercise(exercise, userLogin);

        // Call programming-exercises/new-result which includes build log entries
        postResultWithBuildLogs(participation.getBuildPlanId(), HttpStatus.OK, false);
        assertBuildError(participation.getId(), userLogin, programmingLanguage);

        // Do another call to new-result again and assert that no new submission is created.
        var submission = submissionRepository.findAll().get(0);
        postResultWithBuildLogs(participation.getBuildPlanId(), HttpStatus.OK, false);
        assertNoNewSubmissionsAndIsSubmission(submission);
    }

    private void assertNoNewSubmissionsAndIsSubmission(ProgrammingSubmission submission) {
        var submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);
        assertThat(submissions.get(0).getId()).isEqualTo(submission.getId());
    }

    @NotNull
    private StudentExam createEndedStudentExamWithGracePeriod(User user, Integer gracePeriod) {
        var course = database.addEmptyCourse();
        var exam = database.addActiveExamWithRegisteredUser(course, user);
        exam = database.addExerciseGroupsAndExercisesToExam(exam, true);
        exam.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam.addRegisteredUser(user);
        exam.setGracePeriod(gracePeriod);
        exam = examRepository.save(exam);

        var studentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(user.getId(), exam.getId()).get();
        studentExam.setWorkingTime((int) Duration.between(exam.getStartDate(), exam.getEndDate()).getSeconds());
        studentExam.setExercises(new ArrayList<>(exam.getExerciseGroups().get(6).getExercises()));
        studentExam.setUser(user);
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldCreateIllegalSubmissionOnNotifyPushForExamProgrammingExerciseAfterDueDateWithoutGracePeriod() throws Exception {
        var user = userRepository.findUserWithGroupsAndAuthoritiesByLogin("student1").get();

        // The exam has to be over.
        // Create an exam with programming exercise and no grace period.
        StudentExam studentExam = createEndedStudentExamWithGracePeriod(user, 0);

        // Add a participation for the programming exercise
        ProgrammingExercise programmingExercise = (ProgrammingExercise) studentExam.getExercises().get(0);
        var participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, user.getLogin());

        // mock request for fetchCommitInfo()
        final String projectKey = "test201904bprogrammingexercise6";
        final String slug = "test201904bprogrammingexercise6-exercise-testuser";
        final String hash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        bitbucketRequestMockProvider.mockFetchCommitInfo(projectKey, slug, hash);
        bitbucketRequestMockProvider.mockGetDefaultBranch(defaultBranch, programmingExercise.getProjectKey());
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(programmingExercise);
        bitbucketRequestMockProvider.mockGetPushDate(programmingExercise.getProjectKey(), "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d", ZonedDateTime.now());
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, programmingExercise.getProjectKey());
        ProgrammingSubmission submission = mockCommitInfoAndPostSubmission(participation.getId());

        // Mock result from bamboo
        assertThat(examDateService.getLatestIndividualExamEndDateWithGracePeriod(studentExam.getExam())).isBefore(ZonedDateTime.now());
        postResult(participation.getBuildPlanId(), HttpStatus.OK, false);

        // Check that the result was created successfully and is linked to the participation and submission.
        List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId());
        assertThat(results).hasSize(1);
        Result createdResult = results.get(0);
        createdResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(createdResult.getId()).get();

        // Student should not receive a result over WebSocket, the exam is over and therefore test after due date would be visible
        verify(messagingTemplate, never()).convertAndSendToUser(eq(user.getLogin()), eq(NEW_RESULT_TOPIC), isA(Result.class));

        // Assert that the submission is illegal
        assertThat(submission.getParticipation().getId()).isEqualTo(participation.getId());
        var illegalSubmission = submissionRepository.findWithEagerResultsById(submission.getId()).get();
        assertThat(illegalSubmission.getType()).isEqualTo(SubmissionType.ILLEGAL);
        assertThat(illegalSubmission.isSubmitted()).isTrue();
        assertThat(illegalSubmission.getLatestResult().isRated()).isFalse();
        assertThat(illegalSubmission.getLatestResult().getId()).isEqualTo(createdResult.getId());

        // Check that the result belongs to the participation
        Participation updatedParticipation = participationRepository.findByIdWithResultsAndSubmissionsResults(participation.getId()).orElseThrow();
        assertThat(createdResult.getParticipation().getId()).isEqualTo(updatedParticipation.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldCreateLegalSubmissionOnNotifyPushForExamProgrammingExerciseAfterDueDateWithGracePeriod() throws Exception {
        var user = userRepository.findUserWithGroupsAndAuthoritiesByLogin("student1").get();

        // The exam has to be over.
        // Create an exam with programming exercise and no grace period.
        StudentExam studentExam = createEndedStudentExamWithGracePeriod(user, 180);

        // Add a participation for the programming exercise
        ProgrammingExercise programmingExercise = (ProgrammingExercise) studentExam.getExercises().get(0);

        var participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, user.getLogin());

        // set the author name to "Artemis"
        ProgrammingSubmission submission = mockCommitInfoAndPostSubmission(participation.getId());

        // Mock result from bamboo
        assertThat(examDateService.getLatestIndividualExamEndDateWithGracePeriod(studentExam.getExam())).isAfter(ZonedDateTime.now());
        postResult(participation.getBuildPlanId(), HttpStatus.OK, false);

        // Check that the result was created successfully and is linked to the participation and submission.
        List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId());
        assertThat(results).hasSize(1);
        Result createdResult = results.get(0);
        createdResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(createdResult.getId()).get();

        // Student should receive a result over WebSocket, the exam not over (grace period still active)
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(user.getLogin()), eq(NEW_RESULT_TOPIC), isA(Result.class));

        // Assert that the submission is illegal
        assertThat(submission.getParticipation().getId()).isEqualTo(participation.getId());
        var illegalSubmission = submissionRepository.findWithEagerResultsById(submission.getId()).get();
        assertThat(illegalSubmission.getType()).isEqualTo(SubmissionType.MANUAL);
        assertThat(illegalSubmission.isSubmitted()).isTrue();
        assertThat(illegalSubmission.getLatestResult().isRated()).isTrue();
        assertThat(illegalSubmission.getLatestResult().getId()).isEqualTo(createdResult.getId());

        // Check that the result belongs to the participation
        Participation updatedParticipation = participationRepository.findByIdWithResultsAndSubmissionsResults(participation.getId()).orElseThrow();
        assertThat(createdResult.getParticipation().getId()).isEqualTo(updatedParticipation.getId());
    }

    private Result assertBuildError(Long participationId, String userLogin, ProgrammingLanguage programmingLanguage) throws Exception {
        // Assert that result linked to the participation
        var results = resultRepository.findAllByParticipationIdOrderByCompletionDateDesc(participationId);
        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.getHasFeedback()).isFalse();
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getScore()).isZero();
        assertThat(result.getResultString()).isEqualTo("No tests found");

        // Assert that the submission linked to the participation
        var submission = (ProgrammingSubmission) result.getSubmission();
        assertThat(submission).isNotNull();
        assertThat(submission.isBuildFailed()).isTrue();
        var submissionWithLogsOptional = submissionRepository.findWithEagerBuildLogEntriesById(submission.getId());

        // Assert that build logs have been saved in the build log repository.
        assertThat(submissionWithLogsOptional).isPresent();
        var submissionWithLogs = submissionWithLogsOptional.get();
        assertThat(submissionWithLogs.getBuildLogEntries()).hasSize(programmingLanguage.equals(C) ? 8 : 7);

        // Assert that the build logs can be retrieved from the REST API
        database.changeUser(userLogin);
        var receivedLogs = request.get("/api/repository/" + participationId + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotNull();
        assertThat(receivedLogs).hasSameSizeAs(submissionWithLogs.getBuildLogEntries());

        return result;
    }

    /**
     * This is the simulated request from the VCS to Artemis on a new commit.
     */
    private ProgrammingSubmission postSubmission(Long participationId, HttpStatus expectedStatus) throws Exception {
        return testService.postSubmission(participationId, expectedStatus, BITBUCKET_PUSH_EVENT_REQUEST);
    }

    /**
     * Simulate a commit to the test repository, this executes a http request from the VCS to Artemis.
     */
    @SuppressWarnings("unchecked")
    private void postTestRepositorySubmission() throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BITBUCKET_PUSH_EVENT_REQUEST);

        Map<String, Object> requestBodyMap = (Map<String, Object>) obj;
        List<Map<String, Object>> changes = (List<Map<String, Object>>) requestBodyMap.get("changes");
        changes.get(0).put("toHash", TEST_COMMIT);

        // Api should return ok.
        request.postWithoutLocation(TEST_CASE_CHANGED_API_PATH + exerciseId, obj, HttpStatus.OK, new HttpHeaders());
    }

    /**
     * Simulate a commit to the test repository, this executes a http request from the VCS to Artemis.
     */
    @SuppressWarnings("unchecked")
    private void postTestRepositorySubmissionWithoutCommit(HttpStatus status) throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BITBUCKET_PUSH_EVENT_REQUEST_WITHOUT_COMMIT);
        request.postWithoutLocation(TEST_CASE_CHANGED_API_PATH + exerciseId, obj, status, new HttpHeaders());
    }

    private String getBuildPlanIdByParticipationType(IntegrationTestParticipationType participationType, int participationIndex) {
        return switch (participationType) {
            case TEMPLATE -> "BASE";
            case SOLUTION -> "SOLUTION";
            default -> getStudentLoginFromParticipation(participationIndex);
        };
    }

    private void triggerBuild(IntegrationTestParticipationType participationType, int participationIndex) throws Exception {
        Long id = getParticipationIdByType(participationType, participationIndex);
        request.postWithoutLocation("/api/programming-submissions/" + id + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
    }

    private void triggerInstructorBuild(IntegrationTestParticipationType participationType, int participationIndex) throws Exception {
        Long id = getParticipationIdByType(participationType, participationIndex);
        request.postWithoutLocation("/api/programming-submissions/" + id + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.OK, new HttpHeaders());
    }

    /**
     * This is the simulated request from the CI to Artemis on a new build result.
     */
    private void postResult(IntegrationTestParticipationType participationType, int participationIndex, HttpStatus expectedStatus, boolean additionalCommit) throws Exception {
        String buildPlanStudentId = getBuildPlanIdByParticipationType(participationType, participationIndex);
        postResult(exercise.getProjectKey().toUpperCase() + "-" + buildPlanStudentId, expectedStatus, additionalCommit);
    }

    private void postResultWithBuildLogs(String participationId, HttpStatus expectedStatus, boolean additionalCommit) throws Exception {
        var bambooBuildResult = ModelFactory.generateBambooBuildResultWithLogs(ASSIGNMENT_REPO_NAME, List.of(), List.of());
        postResult(participationId, bambooBuildResult, expectedStatus, additionalCommit);
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
        request.postWithoutLocation("/api/" + NEW_RESULT_RESOURCE_PATH, alteredObj, expectedStatus, httpHeaders);
    }

    private BambooBuildResultNotificationDTO createBambooBuildResultNotificationDTO() throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BAMBOO_BUILD_RESULT_REQUEST);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper.convertValue(obj, BambooBuildResultNotificationDTO.class);
    }

    private String getStudentLoginFromParticipation(int participationIndex) {
        StudentParticipation participation = studentParticipationRepository.findWithStudentById(participationIds.get(participationIndex)).get();
        return participation.getParticipantIdentifier();
    }

    private Long getParticipationIdByType(IntegrationTestParticipationType participationType, int participationIndex) {
        return switch (participationType) {
            case SOLUTION -> solutionParticipationId;
            case TEMPLATE -> templateParticipationId;
            default -> participationIds.get(participationIndex);
        };
    }

    private void setBuildAndTestAfterDueDateForProgrammingExercise(ZonedDateTime buildAndTestAfterDueDate) {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exerciseId).get();
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(buildAndTestAfterDueDate);
        programmingExerciseRepository.save(programmingExercise);
    }
}
