package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.NEW_SUBMISSION_TOPIC;
import static de.tum.in.www1.artemis.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildPlanDTO;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.TestConstants;

public class ProgrammingSubmissionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingSubmissionRepository submissionRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private ProgrammingExercise exercise;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    @BeforeEach
    public void init() {
        database.addUsers(10, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(0);
        database.addSolutionParticipationForProgrammingExercise(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addProgrammingParticipationWithResultForExercise(exercise, "student1");
        exercise.setTestCasesChanged(true);
        programmingExerciseRepository.save(exercise);

        programmingExerciseStudentParticipation = database.addStudentParticipationForProgrammingExercise(exercise, "student2");

        var newObjectId = new ObjectId(4, 5, 2, 5, 3);
        doReturn(newObjectId).when(gitService).getLastCommitHash(null);
        doReturn(newObjectId).when(gitService).getLastCommitHash(exercise.getTemplateParticipation().getVcsRepositoryUrl());
        var dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(programmingExerciseStudentParticipation.getVcsRepositoryUrl());
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildStudent() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);
        bambooRequestMockProvider.mockTriggerBuild((ProgrammingExerciseParticipation) participation);
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());

        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);

        ProgrammingSubmission submission = submissions.get(0);
        assertThat(submissionRepository.findWithEagerResultsById(submission.getId()).get().getLatestResult()).isNull();
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(submission.getType()).isEqualTo(SubmissionType.MANUAL);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void triggerBuildInstructor() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);
        bambooRequestMockProvider.mockTriggerBuild((ProgrammingExerciseParticipation) participation);
        bambooRequestMockProvider.mockTriggerBuild((ProgrammingExerciseParticipation) participation);
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.OK, new HttpHeaders());

        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);

        ProgrammingSubmission submission = submissions.get(0);
        assertThat(submissionRepository.findWithEagerResultsById(submission.getId()).get().getLatestResult()).isNull();
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(submission.getType()).isEqualTo(SubmissionType.INSTRUCTOR);

        // Trigger the call again and make sure that the submission shouldn't be recreated
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.OK, new HttpHeaders());
        var updatedSubmissions = submissionRepository.findAll();
        assertThat(updatedSubmissions).hasSize(1);
        assertThat(updatedSubmissions.get(0).getId()).isEqualTo(submission.getId());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void triggerBuildInstructor_tutorForbidden() throws Exception {
        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.FORBIDDEN,
                new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildInstructor_studentForbidden() throws Exception {
        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.FORBIDDEN,
                new HttpHeaders());
    }

    @Test
    @Timeout(5)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void triggerBuildForExercise_Instructor() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
        String login1 = "student1";
        String login2 = "student2";
        String login3 = "student3";
        final var firstParticipation = database.addStudentParticipationForProgrammingExercise(exercise, login1);
        final var secondParticipation = database.addStudentParticipationForProgrammingExercise(exercise, login2);
        final var thirdParticipation = database.addStudentParticipationForProgrammingExercise(exercise, login3);
        // Set test cases changed to true; after the build run it should be false;
        exercise.setTestCasesChanged(true);
        programmingExerciseRepository.save(exercise);

        bambooRequestMockProvider.mockTriggerBuild(firstParticipation);
        bambooRequestMockProvider.mockTriggerBuild(secondParticipation);
        bambooRequestMockProvider.mockTriggerBuild(thirdParticipation);
        // Each trigger build is mocked twice per participation so that we test
        // that no new submission is created on re-trigger
        bambooRequestMockProvider.mockTriggerBuild(firstParticipation);
        bambooRequestMockProvider.mockTriggerBuild(secondParticipation);
        bambooRequestMockProvider.mockTriggerBuild(thirdParticipation);

        // Perform a call to trigger-instructor-build-all twice. We want to check that the submissions
        // aren't being re-created.
        var url = "/api/programming-exercises/" + exercise.getId() + "/trigger-instructor-build-all";
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());

        await().until(() -> submissionRepository.count() >= 3);

        List<ProgrammingSubmission> submissions = submissionRepository.findAll();

        // Make sure submissions aren't re-created.
        assertThat(submissions.size()).isEqualTo(3);

        List<ProgrammingExerciseParticipation> participations = new ArrayList<>();
        for (ProgrammingSubmission submission : submissions) {
            assertThat(submissionRepository.findWithEagerResultsById(submission.getId()).get().getLatestResult()).isNull();
            assertThat(submission.isSubmitted()).isTrue();
            assertThat(submission.getType()).isEqualTo(SubmissionType.INSTRUCTOR);
            assertThat(submission.getParticipation()).isNotNull();
            // There should be no participation assigned to two submissions.
            assertThat(participations.stream().noneMatch(p -> p.equals(submission.getParticipation()))).isTrue();
            participations.add((ProgrammingExerciseParticipation) submission.getParticipation());
        }

        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exercise.getId())
                .get();
        assertThat(updatedProgrammingExercise.getTestCasesChanged()).isFalse();
        verify(groupNotificationService, times(1)).notifyInstructorGroupAboutExerciseUpdate(updatedProgrammingExercise, Constants.TEST_CASES_CHANGED_RUN_COMPLETED_NOTIFICATION);
        verify(websocketMessagingService, times(1)).sendMessage("/topic/programming-exercises/" + exercise.getId() + "/test-cases-changed", false);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void triggerBuildForExercise_tutorForbidden() throws Exception {
        request.postWithoutLocation("/api/programming-exercises/" + 1L + "/trigger-instructor-build-all", null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildForExercise_studentForbidden() throws Exception {
        request.postWithoutLocation("/api/programming-exercises/" + 1L + "/trigger-instructor-build-all", null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void triggerBuildForParticipations_instructor() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        String login1 = "student1";
        String login2 = "student2";
        String login3 = "student3";
        ProgrammingExerciseStudentParticipation participation1 = database.addStudentParticipationForProgrammingExercise(exercise, login1);
        ProgrammingExerciseStudentParticipation participation2 = database.addStudentParticipationForProgrammingExercise(exercise, login2);
        ProgrammingExerciseStudentParticipation participation3 = database.addStudentParticipationForProgrammingExercise(exercise, login3);

        // We only trigger two participations here: 1 and 3.
        bambooRequestMockProvider.mockTriggerBuild(participation1);
        bambooRequestMockProvider.mockTriggerBuild(participation3);

        // Mock again because we call the trigger request two times
        bambooRequestMockProvider.mockTriggerBuild(participation1);
        bambooRequestMockProvider.mockTriggerBuild(participation3);

        List<Long> participationsToTrigger = new ArrayList<>(Arrays.asList(participation1.getId(), participation3.getId()));

        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());

        // Perform a call to trigger-instructor-build-all twice. We want to check that the submissions
        // aren't being re-created.
        var url = "/api/programming-exercises/" + exercise.getId() + "/trigger-instructor-build";
        request.postWithoutLocation(url, participationsToTrigger, HttpStatus.OK, new HttpHeaders());
        request.postWithoutLocation(url, participationsToTrigger, HttpStatus.OK, new HttpHeaders());

        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(2);

        List<ProgrammingExerciseStudentParticipation> participations = new ArrayList<>();
        for (ProgrammingSubmission submission : submissions) {
            assertThat(submissionRepository.findWithEagerResultsById(submission.getId()).get().getLatestResult()).isNull();
            assertThat(submission.isSubmitted()).isTrue();
            assertThat(submission.getType()).isEqualTo(SubmissionType.INSTRUCTOR);
            assertThat(submission.getParticipation()).isNotNull();
            // There should be no submission for the participation that was not sent to the endpoint.
            assertThat(submission.getParticipation().getId()).isNotEqualTo(participation2.getId());
            // There should be no participation assigned to two submissions.
            assertThat(participations.stream().noneMatch(p -> p.equals(submission.getParticipation()))).isTrue();
            participations.add((ProgrammingExerciseStudentParticipation) submission.getParticipation());
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void triggerBuildForParticipations_tutorForbidden() throws Exception {
        request.postWithoutLocation("/api/programming-exercises/" + 1L + "/trigger-instructor-build", new ArrayList<>(), HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildForParticipations_studentForbidden() throws Exception {
        request.postWithoutLocation("/api/programming-exercises/" + 1L + "/trigger-instructor-build", new ArrayList<>(), HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void triggerFailedBuild_resultPresentInCI_ok() throws Exception {
        var user = database.getUserByLogin("student1");
        var submission = new ProgrammingSubmission();
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(4));
        submission.setSubmitted(true);
        submission.setCommitHash(TestConstants.COMMIT_HASH_STRING);
        submission.setType(SubmissionType.MANUAL);
        submission = database.addProgrammingSubmission(exercise, submission, "student1");
        final var participation = programmingExerciseStudentParticipationRepository.findById(submission.getParticipation().getId()).get();
        bambooRequestMockProvider.enableMockingOfRequests();
        var buildPlan = new BambooBuildPlanDTO(true, false);
        bambooRequestMockProvider.mockGetBuildPlan(participation.getBuildPlanId(), buildPlan, false);
        // Mock again because we call the trigger request two times
        bambooRequestMockProvider.mockGetBuildPlan(participation.getBuildPlanId(), buildPlan, false);

        var url = "/api" + Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH + participation.getId() + "/trigger-failed-build";
        request.postWithoutLocation(url, null, HttpStatus.OK, null);

        verify(messagingTemplate).convertAndSendToUser(user.getLogin(), NEW_SUBMISSION_TOPIC, submission);

        // Perform the request again and make sure no new submission was created
        request.postWithoutLocation(url, null, HttpStatus.OK, null);
        var updatedSubmissions = submissionRepository.findAll();
        assertThat(updatedSubmissions.size()).isEqualTo(1);
        assertThat(updatedSubmissions.get(0).getId()).isEqualTo(submission.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getAllProgrammingSubmissions_asUser_forbidden() throws Exception {
        request.get("/api/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllProgrammingSubmissions_asInstructor_allSubmissionsReturned() throws Exception {
        final var submissions = new LinkedList<ProgrammingSubmission>();
        for (int i = 1; i < 4; i++) {
            final var submission = ModelFactory.generateProgrammingSubmission(true);
            submissions.add(submission);
            database.addProgrammingSubmission(exercise, submission, "student" + i);
        }

        final var responseSubmissions = request.getList("/api/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class);

        assertThat(responseSubmissions).containsExactly(submissions.toArray(new ProgrammingSubmission[0]));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getAllProgrammingSubmissions_assessedByTutor_allSubmissionsReturned() throws Exception {
        database.addProgrammingSubmission(exercise, ModelFactory.generateProgrammingSubmission(true), "student1");
        var assessedSubmission = ModelFactory.generateProgrammingSubmission(true);
        assessedSubmission = database.addProgrammingSubmission(exercise, assessedSubmission, "student2");
        final var tutor = database.getUserByLogin("tutor1");
        database.addResultToSubmission(assessedSubmission, AssessmentType.SEMI_AUTOMATIC, null);
        database.addResultToSubmission(assessedSubmission, AssessmentType.AUTOMATIC, null);
        database.addResultToSubmission(assessedSubmission, AssessmentType.SEMI_AUTOMATIC, tutor);

        final var paramMap = new LinkedMultiValueMap<String, String>();
        paramMap.add("assessedByTutor", "true");
        final var responseSubmissions = request.getList("/api/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class, paramMap);

        assertThat(responseSubmissions).containsExactly(assessedSubmission);
        assertThat(responseSubmissions.get(0).getResults().size()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getProgrammingSubmissionWithoutAssessment_asTutorWithOneAvailable_returnsSubmission() throws Exception {
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        final var submission = database.addProgrammingSubmission(exercise, ModelFactory.generateProgrammingSubmission(true), "student1");
        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null);

        final var responseSubmission = request.get("/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment", HttpStatus.OK, ProgrammingSubmission.class);

        assertThat(responseSubmission).isEqualTo(submission);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testLockAndGetProgrammingSubmission_withManualResult() throws Exception {
        ProgrammingSubmission submission = ModelFactory.generateProgrammingSubmission(true);
        submission = database.addProgrammingSubmission(exercise, submission, "student1");
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise = programmingExerciseRepository.save(exercise);
        database.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));
        Result result = database.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now().minusHours(1).minusMinutes(30),
                programmingExerciseStudentParticipation);

        result.setSubmission(submission);
        submission.addResult(result);
        submission.setParticipation(programmingExerciseStudentParticipation);
        submissionRepository.save(submission);
        var submissions = submissionRepository.findAll();

        request.get("/api/programming-submissions/" + programmingExerciseStudentParticipation.getId() + "/lock", HttpStatus.OK, Participation.class);

        // Make sure no new submissions are created
        var latestSubmissions = submissionRepository.findAll();
        assertThat(submissions.size()).isEqualTo(latestSubmissions.size());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testLockAndGetProgrammingSubmission_withoutManualResult() throws Exception {
        var result = database.addResultToParticipation(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusHours(1).minusMinutes(30), programmingExerciseStudentParticipation);
        database.addProgrammingSubmissionToResultAndParticipation(result, programmingExerciseStudentParticipation, "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d");
        exercise.setAssessmentType(AssessmentType.AUTOMATIC);
        exercise = programmingExerciseRepository.save(exercise);
        database.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));
        var submissions = submissionRepository.findAll();

        request.get("/api/programming-submissions/" + programmingExerciseStudentParticipation.getId() + "/lock", HttpStatus.FORBIDDEN, Participation.class);

        // Make sure no new submissions are created
        var latestSubmissions = submissionRepository.findAll();
        assertThat(submissions.size()).isEqualTo(latestSubmissions.size());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetProgrammingSubmissionWithoutAssessment() throws Exception {
        ProgrammingSubmission submission = ModelFactory.generateProgrammingSubmission(true);
        submission = database.addProgrammingSubmission(exercise, submission, "student1");
        database.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        ProgrammingSubmission storedSubmission = request.get("/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment", HttpStatus.OK,
                ProgrammingSubmission.class);

        // set dates to UTC and round to milliseconds for comparison
        submission.setSubmissionDate(ZonedDateTime.ofInstant(submission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        storedSubmission.setSubmissionDate(ZonedDateTime.ofInstant(storedSubmission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(storedSubmission).as("submission was found").isEqualToIgnoringGivenFields(submission, "result");
        assertThat(storedSubmission.getLatestResult()).as("result is not set").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetProgrammingSubmissionWithoutAssessment_lockSubmission() throws Exception {
        User user = database.getUserByLogin("tutor1");
        var newResult = database.addResultToParticipation(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusHours(2), programmingExerciseStudentParticipation);
        programmingExerciseStudentParticipation.addResult(newResult);
        var submission = database.addProgrammingSubmissionToResultAndParticipation(newResult, programmingExerciseStudentParticipation, "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d");

        database.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        ProgrammingSubmission storedSubmission = request.get("/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment?lock=true", HttpStatus.OK,
                ProgrammingSubmission.class);

        assertThat(storedSubmission.getLatestResult()).as("result is set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        var automaticResults = storedSubmission.getLatestResult().getFeedbacks().stream().filter(feedback -> feedback.getType() == FeedbackType.AUTOMATIC)
                .collect(Collectors.toList());
        assertThat(storedSubmission.getLatestResult().getFeedbacks().size()).isEqualTo(automaticResults.size());
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        assertThat(storedSubmission.getLatestResult().getResultString()).isEqualTo(submission.getLatestResult().getResultString());

        // Make sure no new submissions are created
        var latestSubmissions = submissionRepository.findAll();
        assertThat(latestSubmissions.size()).isEqualTo(1);
        assertThat(latestSubmissions.get(0).getId()).isEqualTo(submission.getId());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetModelSubmissionWithoutAssessment_testLockLimit() throws Exception {
        createTenLockedSubmissionsForExercise("tutor1");
        database.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment", HttpStatus.BAD_REQUEST, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getProgrammingSubmissionWithoutAssessment_dueDateNotPassedYet() throws Exception {
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        final var submission = database.addProgrammingSubmission(exercise, ModelFactory.generateProgrammingSubmission(true), "student1");
        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null);

        request.get("/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getProgrammingSubmissionWithoutAssessment_alreadyAssessed_noFound() throws Exception {
        exercise.setDueDate(ZonedDateTime.now().minusDays(2));
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        var submission = ModelFactory.generateProgrammingSubmission(true);
        submission = database.addProgrammingSubmission(exercise, submission, "student1");
        final var tutor = database.getUserByLogin("tutor1");
        database.addResultToSubmission(submission, AssessmentType.SEMI_AUTOMATIC, tutor);

        request.get("/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment", HttpStatus.NOT_FOUND, String.class);
    }

    private void createTenLockedSubmissionsForExercise(String assessor) {
        ProgrammingSubmission submission;
        for (int i = 1; i < 11; i++) {
            submission = ModelFactory.generateProgrammingSubmission(true);
            database.addProgrammingSubmissionWithResultAndAssessor(exercise, submission, "student" + i, assessor, AssessmentType.SEMI_AUTOMATIC, false);
        }
    }
}
