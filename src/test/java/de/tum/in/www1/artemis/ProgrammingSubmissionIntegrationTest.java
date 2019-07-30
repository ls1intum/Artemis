package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.config.Constants.*;
import static de.tum.in.www1.artemis.constants.ProgrammingSubmissionConstants.BAMBOO_REQUEST;
import static de.tum.in.www1.artemis.constants.ProgrammingSubmissionConstants.BITBUCKET_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.stream.Collectors;

import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.service.connectors.BitbucketService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.ProgrammingSubmissionResource;
import de.tum.in.www1.artemis.web.rest.ResultResource;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis, bamboo, bitbucket")
class ProgrammingSubmissionIntegrationTest {

    private enum IntegrationTestParticipationType {
        STUDENT, TEMPLATE, SOLUTION
    }

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ProgrammingSubmissionResource programmingSubmissionResource;

    @Autowired
    ResultResource resultResource;

    @Autowired
    ProgrammingSubmissionService programmingSubmissionService;

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
    BitbucketService versionControlService;

    private Long exerciseId;

    private Long templateParticipationId;

    private Long solutionParticipationId;

    private List<Long> participationIds;

    @BeforeEach
    void reset() {
        database.resetDatabase();
        database.addUsers(2, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        SecurityUtils.setAuthorizationObject();
        ProgrammingExercise exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(0);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);
        database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        database.addStudentParticipationForProgrammingExercise(exercise, "student2");

        exerciseId = exercise.getId();
        templateParticipationId = exercise.getTemplateParticipation().getId();
        solutionParticipationId = exercise.getSolutionParticipation().getId();
        participationIds = exercise.getParticipations().stream().map(Participation::getId).collect(Collectors.toList());
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     *
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

    @TestFactory
    Collection<DynamicTest> shouldCreateSubmissionOnNotifyPushForSubmissionTestCollection() {
        return Arrays.stream(IntegrationTestParticipationType.values())
                .map(participationType -> DynamicTest.dynamicTest("shouldCreateSubmissionOnNotifyPushFromVCS_for_" + participationType, () -> {
                    // In dynamic tests, the BeforeEach annotation does not work, so reset is called manually here.
                    reset();
                    shouldCreateSubmissionOnNotifyPushForSubmission(participationType);
                })).collect(Collectors.toList());
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     *
     * However the participation id provided by the VCS on the request is invalid.
     */
    void shouldCreateSubmissionOnNotifyPushForSubmission(IntegrationTestParticipationType participationType) throws Exception {
        Long participationId = getParticipationIdByType(participationType, 0);
        ProgrammingSubmission submission = postSubmission(participationId, HttpStatus.OK);

        assertThat(submission.getParticipation().getId()).isEqualTo(participationId);
        // Needs to be set for using a custom repository method, known spring bug.
        SecurityUtils.setAuthorizationObject();
        Participation updatedParticipation = participationRepository.getOneWithEagerSubmissions(participationId);
        assertThat(updatedParticipation.getSubmissions().size()).isEqualTo(1);
        assertThat(updatedParticipation.getSubmissions().stream().findFirst().get().getId()).isEqualTo(submission.getId());

        // Make sure the submission has the correct commit hash.
        assertThat(submission.getCommitHash()).isEqualTo("9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d");
        // The submission should be manual and submitted.
        assertThat(submission.getType()).isEqualTo(SubmissionType.MANUAL);
        assertThat(submission.isSubmitted()).isTrue();
    }

    @TestFactory
    Collection<DynamicTest> shouldHandleNewBuildResultCreatedByCommitTestCollection() {
        return Arrays.stream(IntegrationTestParticipationType.values())
                .map(participationType -> DynamicTest.dynamicTest("shouldHandleNewBuildResultCreatedByCommit" + participationType, () -> {
                    // In dynamic tests, the BeforeEach annotation does not work, so reset is called manually here.
                    reset();
                    shouldHandleNewBuildResultCreatedByCommit(participationType);
                })).collect(Collectors.toList());
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     *
     * Here the participation provided does exist so Artemis can create the submission.
     *
     * After that the CI builds the code submission and notifies Artemis so it can create the result.
     */
    void shouldHandleNewBuildResultCreatedByCommit(IntegrationTestParticipationType participationType) throws Exception {
        Long participationId = getParticipationIdByType(participationType, 0);
        ProgrammingSubmission submission = postSubmission(participationId, HttpStatus.OK);
        final long submissionId = submission.getId();
        postResult(participationType, 0, HttpStatus.OK);

        // Check that the result was created successfully and is linked to the participation and submission.
        List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId);
        assertThat(results).hasSize(1);
        Result createdResult = results.get(0);
        // Needs to be set for using a custom repository method, known spring bug.
        SecurityUtils.setAuthorizationObject();
        Participation participation = participationRepository.getOneWithEagerSubmissions(participationId);
        submission = submissionRepository.findByIdWithEagerResult(submission.getId());
        assertThat(createdResult.getParticipation().getId()).isEqualTo(participation.getId());
        assertThat(submission.getResult().getId()).isEqualTo(createdResult.getId());
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getSubmissions().stream().anyMatch(s -> s.getId().equals(submissionId))).isTrue();
    }

    @TestFactory
    Collection<DynamicTest> shouldNotLinkTwoResultsToTheSameSubmissionTestCollection() {
        return Arrays.stream(IntegrationTestParticipationType.values())
                .map(participationType -> DynamicTest.dynamicTest("shouldNotLinkTwoResultsToTheSameSubmission" + participationType, () -> {
                    // In dynamic tests, the BeforeEach annotation does not work, so reset is called manually here.
                    reset();
                    shouldNotLinkTwoResultsToTheSameSubmission(participationType);
                })).collect(Collectors.toList());
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     *
     * After that the CI builds the code submission and notifies Artemis so it can create the result - however for an unknown reason this request is sent twice!
     *
     * Only the last result should be linked to the created submission.
     */
    void shouldNotLinkTwoResultsToTheSameSubmission(IntegrationTestParticipationType participationType) throws Exception {
        Long participationId = getParticipationIdByType(participationType, 0);
        // Create 1 submission.
        postSubmission(participationId, HttpStatus.OK);
        // Create 2 results for the same submission.
        postResult(participationType, 0, HttpStatus.OK);
        postResult(participationType, 0, HttpStatus.INTERNAL_SERVER_ERROR);

        // Make sure there is still only 1 submission.
        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);
        SecurityUtils.setAuthorizationObject();
        ProgrammingSubmission submission = submissionRepository.findByIdWithEagerResult(submissions.get(0).getId());

        // There should now be 1 results linked to the submission.
        List<Result> results = resultRepository.findAll();
        assertThat(results).hasSize(1);
        Result result1 = resultRepository.findWithEagerSubmissionAndFeedbackById(results.get(0).getId()).get();
        assertThat(result1.getSubmission()).isNotNull();
        assertThat(submission.getResult().getId()).isEqualTo(result1.getId());
    }

    @TestFactory
    Collection<DynamicTest> shouldNotCreateTwoSubmissionsForTwoIdenticalCommitsTestCollection() {
        return Arrays.stream(IntegrationTestParticipationType.values())
                .map(participationType -> DynamicTest.dynamicTest("shouldNotCreateTwoSubmissionsForTwoIdenticalCommits" + participationType, () -> {
                    // In dynamic tests, the BeforeEach annotation does not work, so reset is called manually here.
                    reset();
                    shouldNotCreateTwoSubmissionsForTwoIdenticalCommits(participationType);
                })).collect(Collectors.toList());
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission - however for an unknown reason this request is sent twice!
     *
     * This should not create two identical submissions.
     */
    void shouldNotCreateTwoSubmissionsForTwoIdenticalCommits(IntegrationTestParticipationType participationType) throws Exception {
        Long participationId = getParticipationIdByType(participationType, 0);
        // Post the same submission twice.
        ProgrammingSubmission submission = postSubmission(participationId, HttpStatus.OK);
        postSubmission(participationId, HttpStatus.BAD_REQUEST);
        // Post the build result once.
        postResult(participationType, 0, HttpStatus.OK);

        // There should only be one submission and this submission should be linked to the created result.
        List<Result> results = resultRepository.findAll();
        assertThat(results).hasSize(1);
        Result result = resultRepository.findWithEagerSubmissionAndFeedbackById(results.get(0).getId()).get();
        submission = submissionRepository.findById(submission.getId()).get();
        assertThat(result.getSubmission()).isNotNull();
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());
        assertThat(submission.getResult()).isNotNull();
        assertThat(submission.getResult().getId()).isEqualTo(result.getId());
    }

    @TestFactory
    Collection<DynamicTest> shouldCreateSubmissionForManualBuildRunTestFactory() {
        return Arrays.stream(IntegrationTestParticipationType.values())
                .map(participationType -> DynamicTest.dynamicTest("shouldCreateSubmissionForManualBuildRun" + participationType, () -> {
                    // In dynamic tests, the BeforeEach annotation does not work, so reset is called manually here.
                    reset();
                    shouldCreateSubmissionForManualBuildRun(participationType);
                })).collect(Collectors.toList());
    }

    /**
     * This is the case where an instructor manually triggers the build from the CI.
     * Here no submission exists yet and now needs to be created on the result notification.
     */
    void shouldCreateSubmissionForManualBuildRun(IntegrationTestParticipationType participationType) throws Exception {
        Long participationId = getParticipationIdByType(participationType, 0);
        postResult(participationType, 0, HttpStatus.OK);

        SecurityUtils.setAuthorizationObject();
        Participation participation = participationRepository.getOneWithEagerSubmissions(participationId);

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
     * After a commit into the test repository, the VCS triggers Artemis to create submissions for all participations of the given exercise.
     * The reason for this is that the test repository update will trigger a build run in the CI for every participation.
     */
    /*
     * @Test void shouldCreateStudentSubmissionsForAllParticipationsOfExerciseAfterTestRepositoryCommit() throws Exception { // Phase 1: There has been a commit to the test
     * repository, the VCS now informs Artemis about it. postTestRepositorySubmission(); // There are two student participations, so after the test notification two new submissions
     * should have been created. List<Participation> participations = new ArrayList<>();
     * participations.add(participationRepository.getOneWithEagerSubmissions(templateParticipationId));
     * participations.add(participationRepository.getOneWithEagerSubmissions(solutionParticipationId));
     * participations.add(participationRepository.getOneWithEagerSubmissions(participationIds.get(0)));
     * participations.add(participationRepository.getOneWithEagerSubmissions(participationIds.get(1))); List<ProgrammingSubmission> submissions = submissionRepository.findAll();
     * assertThat(submissions).hasSize(4); // There should be a 1-1 relationship from submissions to participations.
     * assertThat(submissions.get(0).getParticipation().getId().equals(participations.get(0).getId())).isTrue();
     * assertThat(submissions.get(1).getParticipation().getId().equals(participations.get(1).getId())).isTrue();
     * assertThat(submissions.get(2).getParticipation().getId().equals(participations.get(2).getId())).isTrue();
     * assertThat(submissions.get(3).getParticipation().getId().equals(participations.get(3).getId())).isTrue(); assertThat(submissions.stream().map(s -> (ProgrammingSubmission)
     * s).allMatch(s -> { return s.isSubmitted() && s.getCommitHash().equals("9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d") && s.getType().equals(SubmissionType.TEST); })).isTrue();
     * // Phase 2: Now the CI informs Artemis about the student participation build results. postResult(IntegrationTestParticipationType.STUDENT, 0, HttpStatus.OK);
     * postResult(IntegrationTestParticipationType.STUDENT, 1, HttpStatus.OK); postResult(IntegrationTestParticipationType.TEMPLATE, 0, HttpStatus.OK);
     * postResult(IntegrationTestParticipationType.SOLUTION, 0, HttpStatus.OK); // Now for both student's submission a result should have been created and assigned to the
     * submission. List<Result> results = resultRepository.findAll(); submissions = submissionRepository.findAll(); participations =
     * participationRepository.getAllWithEagerSubmissionsAndResults(); assertThat(participations).hasSize(4); assertThat(results).hasSize(4); for (Result r : results) { boolean
     * hasMatchingSubmission = submissions.stream().anyMatch(s -> s.getId().equals(r.getSubmission().getId())); assertThat(hasMatchingSubmission); } for (Participation p :
     * participations) { assertThat(p.getSubmissions()).hasSize(1); assertThat(p.getResults()).hasSize(1); assertThat(new ArrayList<>(p.getResults()).get(0).getId()).isEqualTo(new
     * ArrayList<>(p.getSubmissions()).get(0).getResult().getId()); } }
     */

    /**
     * This is the simulated request from the VCS to Artemis on a new commit.
     */
    private ProgrammingSubmission postSubmission(Long participationId, HttpStatus expectedStatus) throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BITBUCKET_REQUEST);

        // Api should return ok.
        request.postWithoutLocation("/api" + PROGRAMMING_SUBMISSION_RESOURCE_PATH + participationId, obj, expectedStatus, new HttpHeaders());

        // Submission should have been created for the participation.
        assertThat(submissionRepository.findAll()).hasSize(1);
        // Make sure that both the submission and participation are correctly linked with each other.
        return submissionRepository.findAll().get(0);
    }

    /**
     * Simulate a commit to the test repository, this executes a http request from the VCS to Artemis.
     */
    private void postTestRepositorySubmission() throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BITBUCKET_REQUEST);

        // Api should return ok.
        request.postWithoutLocation(TEST_CASE_CHANGED_API_PATH + exerciseId, obj, HttpStatus.OK, new HttpHeaders());
    }

    /**
     * This is the simulated request from the CI to Artemis on a new build result.
     */
    private void postResult(IntegrationTestParticipationType participationType, int participationNumber, HttpStatus expectedStatus) throws Exception {
        String id;
        switch (participationType) {
        case TEMPLATE:
            id = "BASE";
            break;
        case SOLUTION:
            id = "SOLUTION";
            break;
        default:
            id = getStudentLoginFromParticipation(participationNumber);
        }
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BAMBOO_REQUEST);

        Map<String, Object> requestBodyMap = (Map<String, Object>) obj;
        Map<String, Object> planMap = (Map<String, Object>) requestBodyMap.get("plan");
        planMap.put("key", "TEST201904BPROGRAMMINGEXERCISE6-" + id.toUpperCase());

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", "<secrettoken>");
        request.postWithoutLocation("/api" + NEW_RESULT_RESOURCE_PATH, obj, expectedStatus, httpHeaders);
    }

    private String getStudentLoginFromParticipation(int participationNumber) {
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) participationRepository.getOne(participationIds.get(participationNumber));
        return participation.getStudent().getLogin();
    }

    private Long getParticipationIdByType(IntegrationTestParticipationType participationType, int participationNumber) {
        switch (participationType) {
        case SOLUTION:
            return solutionParticipationId;
        case TEMPLATE:
            return templateParticipationId;
        default:
            return participationIds.get(participationNumber);
        }
    }
}
