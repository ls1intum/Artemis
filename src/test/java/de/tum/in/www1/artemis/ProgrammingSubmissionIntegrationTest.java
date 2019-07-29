package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.config.Constants.*;
import static de.tum.in.www1.artemis.constants.ProgrammingSubmissionConstants.BAMBOO_REQUEST;
import static de.tum.in.www1.artemis.constants.ProgrammingSubmissionConstants.BITBUCKET_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.simple.parser.JSONParser;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.service.connectors.BitbucketService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.ProgrammingSubmissionResource;
import de.tum.in.www1.artemis.web.rest.ResultResource;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis, bamboo, bitbucket")
public class ProgrammingSubmissionIntegrationTest {

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
    SubmissionRepository submissionRepository;

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

    ProgrammingExercise exercise;

    Participation participation;

    ProgrammingSubmission submission;

    @Before
    public void reset() {
        database.resetDatabase();
        database.addUsers(2, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        exercise = programmingExerciseRepository.findAll().get(0);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);
        database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        database.addStudentParticipationForProgrammingExercise(exercise, "student2");
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     *
     * However the participation id provided by the VCS on the request is invalid.
     */
    @Test
    @Transactional(readOnly = true)
    public void shouldNotCreateSubmissionOnNotifyPushForInvalidParticipationId() throws Exception {
        long fakeParticipationId = 9999L;
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BITBUCKET_REQUEST);
        // Api should return not found.
        request.postWithoutLocation("/api" + PROGRAMMING_SUBMISSION_RESOURCE_PATH + fakeParticipationId, obj, HttpStatus.NOT_FOUND, new HttpHeaders());
        // No submission should be created for the fake participation.
        assertThat(submissionRepository.findAll()).hasSize(0);
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     *
     * However the participation id provided by the VCS on the request is invalid.
     */
    @Test
    @Transactional(readOnly = true)
    public void shouldCreateSubmissionOnNotifyPushForStudentSubmission() throws Exception {
        postSubmission(IntegrationTestParticipationType.STUDENT);

        assertThat(submission.getParticipation().getId()).isEqualTo(participation.getId());
        ProgrammingExerciseStudentParticipation updatedParticipation = studentParticipationRepository.getOne(participation.getId());
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
     *
     * Here the participation provided does exist so Artemis can create the submission.
     *
     * After that the CI builds the code submission and notifies Artemis so it can create the result.
     */
    @Test
    @Transactional(readOnly = true)
    public void shouldHandleNewBuildResultCreatedByStudentCommit() throws Exception {
        postSubmission(IntegrationTestParticipationType.STUDENT);
        postStudentResult(getStudentLoginFromParticipation(0));

        // Check that the result was created successfully and is linked to the participation and submission.
        List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId());
        assertThat(results).hasSize(1);
        Result createdResult = results.get(0);
        participation = studentParticipationRepository.getOne(participation.getId());
        submission = (ProgrammingSubmission) submissionRepository.getOne(submission.getId());
        assertThat(createdResult.getParticipation().getId()).isEqualTo(participation.getId());
        assertThat(submission.getResult().getId()).isEqualTo(createdResult.getId());
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getSubmissions().stream().anyMatch(s -> s.getId().equals(submission.getId()))).isTrue();
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission.
     *
     * After that the CI builds the code submission and notifies Artemis so it can create the result - however for an unknown reason this request is sent twice!
     *
     * Only the last result should be linked to the created submission.
     */
    // TODO: Fix defective test.
    @Ignore
    @Test
    @Transactional(readOnly = true)
    public void shouldNotLinkTwoResultsToTheSameSubmission() throws Exception {
        // Create 1 submission.
        postSubmission(IntegrationTestParticipationType.STUDENT);
        // Create 2 results for the same submission.
        postStudentResult(getStudentLoginFromParticipation(0));
        postStudentResult(getStudentLoginFromParticipation(0));

        // Make sure there is still only 1 submission.
        List<Submission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);
        submission = (ProgrammingSubmission) submissions.get(0);

        // There should now be 2 results, but only the last one linked to the submission.
        List<Result> results = resultRepository.findAll();
        assertThat(results).hasSize(2);
        Result result1 = results.get(0);
        Result result2 = results.get(1);
        assertThat(result1.getSubmission()).isNull();
        assertThat(result2.getSubmission()).isNotNull();
        assertThat(result2.getSubmission().getId()).isEqualTo(submission.getId());
        assertThat(submission.getResult().getId()).isEqualTo(result2.getId());
    }

    /**
     * The student commits, the code change is pushed to the VCS.
     * The VCS notifies Artemis about a new submission - however for an unknown reason this request is sent twice!
     *
     * This should not create two identical submissions.
     */
    // TODO: Fix defective test.
    @Ignore
    @Test
    @Transactional(readOnly = true)
    public void shouldNotCreateTwoSubmissionsForTwoIdenticalCommits() throws Exception {
        // Post the same submission twice.
        postSubmission(IntegrationTestParticipationType.STUDENT);
        postSubmission(IntegrationTestParticipationType.STUDENT);
        // Post the build result once.
        postStudentResult(getStudentLoginFromParticipation(0));

        // There should only be one submission and this submission should be linked to the created result.
        List<Result> results = resultRepository.findAll();
        assertThat(results).hasSize(1);
        Result result = results.get(0);
        assertThat(result.getSubmission()).isNotNull();
        assertThat(result.getSubmission().getId()).isEqualTo(submission.getId());
        assertThat(submission.getResult().getId()).isEqualTo(result.getId());
    }

    /**
     * After a commit into the test repository, the VCS triggers Artemis to create submissions for all participations of the given exercise.
     * The reason for this is that the test repository update will trigger a build run in the CI for every participation.
     */
    @Test
    @Transactional(readOnly = true)
    public void shouldCreateStudentSubmissionsForAllParticipationsOfExerciseAfterTestRepositoryCommit() throws Exception {
        // Phase 1: There has been a commit to the test repository, the VCS now informs Artemis about it.
        postTestRepositorySubmission();
        // There are two student participations, so after the test notification two new submissions should have been created.
        List<Participation> participations = new ArrayList<>(exercise.getParticipations());
        List<Submission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(2);
        // There should be a 1-1 relationship from submissions to participations.
        assertThat(submissions.get(0).getParticipation().getId().equals(participations.get(0).getId())
                && submissions.get(1).getParticipation().getId().equals(participations.get(1).getId())).isTrue();
        assertThat(submissions.stream().map(s -> (ProgrammingSubmission) s).allMatch(s -> {
            return s.isSubmitted() && s.getCommitHash().equals("9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d") && s.getType().equals(SubmissionType.TEST);
        })).isTrue();

        // Phase 2: Now the CI informs Artemis about the student participation build results.
        postStudentResult(getStudentLoginFromParticipation(0));
        postStudentResult(getStudentLoginFromParticipation(1));
        // Now for both student's submission a result should have been created and assigned to the submission.
        List<Result> results = resultRepository.findAll();
        submissions = submissionRepository.findAll();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getSubmission().getId().equals(submissions.get(0).getId())).isTrue();
        assertThat(results.get(1).getSubmission().getId().equals(submissions.get(1).getId())).isTrue();
    }

    /**
     * This is the simulated request from the VCS to Artemis on a new commit.
     */
    private void postSubmission(IntegrationTestParticipationType participationType) throws Exception {
        switch (participationType) {
        case SOLUTION:
            participation = exercise.getSolutionParticipation();
        case TEMPLATE:
            participation = exercise.getTemplateParticipation();
        default:
            participation = exercise.getParticipations().stream().findFirst().get();
            break;
        }

        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BITBUCKET_REQUEST);

        // Api should return ok.
        request.postWithoutLocation("/api" + PROGRAMMING_SUBMISSION_RESOURCE_PATH + participation.getId(), obj, HttpStatus.OK, new HttpHeaders());

        // Submission should have been created for the participation.
        assertThat(submissionRepository.findAll()).hasSize(1);
        // Make sure that both the submission and participation are correctly linked with each other.
        submission = (ProgrammingSubmission) submissionRepository.findAll().get(0);
    }

    /**
     * Simulate a commit to the test repository, this executes a http request from the VCS to Artemis.
     */
    private void postTestRepositorySubmission() throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BITBUCKET_REQUEST);

        // Api should return ok.
        request.postWithoutLocation(TEST_CASE_CHANGED_API_PATH + exercise.getId(), obj, HttpStatus.OK, new HttpHeaders());
    }

    /**
     * This is the simulated request from the CI to Artemis on a new build result.
     */
    private void postStudentResult(String user) throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BAMBOO_REQUEST);

        Map<String, Object> requestBodyMap = (Map<String, Object>) obj;
        Map<String, Object> planMap = (Map<String, Object>) requestBodyMap.get("plan");
        planMap.put("key", "TEST201904BPROGRAMMINGEXERCISE6-" + user.toUpperCase());

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", "<secrettoken>");
        request.postWithoutLocation("/api" + NEW_RESULT_RESOURCE_PATH, obj, HttpStatus.OK, httpHeaders);
    }

    private String getStudentLoginFromParticipation(int participationNumber) {
        return new ArrayList<>(this.exercise.getParticipations()).get(participationNumber).getStudent().getLogin();
    }
}
