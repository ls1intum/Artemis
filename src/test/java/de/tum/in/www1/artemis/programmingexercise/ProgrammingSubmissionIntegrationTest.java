package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.NEW_SUBMISSION_TOPIC;
import static de.tum.in.www1.artemis.config.Constants.SETUP_COMMIT_MESSAGE;
import static de.tum.in.www1.artemis.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildPlanDTO;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.TestConstants;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class ProgrammingSubmissionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Value("${artemis.git.name}")
    private String artemisGitName;

    @Value("${artemis.git.email}")
    private String artemisGitEmail;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingSubmissionRepository submissionRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    private ProgrammingExercise exercise;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    @BeforeEach
    void init() {
        database.addUsers(10, 2, 1, 2);
        var course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(exercise.getId()).get();
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
    void tearDown() {
        database.resetDatabase();
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildStudent() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());

        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);
        bambooRequestMockProvider.mockTriggerBuild((ProgrammingExerciseParticipation) participation);

        String url = "/api/programming-submissions/" + participation.getId() + "/trigger-build";
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());

        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);

        ProgrammingSubmission submission = submissions.get(0);
        var optionalSubmission = submissionRepository.findWithEagerResultsById(submission.getId());
        assertThat(optionalSubmission).isPresent();
        assertThat(optionalSubmission.get().getLatestResult()).isNull();
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(submission.getType()).isEqualTo(SubmissionType.MANUAL);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildStudentSubmissionNotFound() throws Exception {
        String login = "student1";
        Course course = database.addCourseWithDifferentModelingExercises();
        ModelingExercise classExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        ModelingSubmission modelingSubmission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/empty-class-diagram.json"),
                true);
        modelingSubmission = database.addModelingSubmission(classExercise, modelingSubmission, login);

        String url = "/api/programming-submissions/" + modelingSubmission.getParticipation().getId() + "/trigger-build";
        request.postWithoutLocation(url, null, HttpStatus.NOT_FOUND, new HttpHeaders());
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
        var optionalSubmission = submissionRepository.findWithEagerResultsById(submission.getId());
        assertThat(optionalSubmission).isPresent();
        assertThat(optionalSubmission.get().getLatestResult()).isNull();
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(submission.getType()).isEqualTo(SubmissionType.INSTRUCTOR);

        // Trigger the call again and make sure that the submission shouldn't be recreated
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.OK, new HttpHeaders());
        var updatedSubmissions = submissionRepository.findAll();
        assertThat(updatedSubmissions).hasSize(1);
        assertThat(updatedSubmissions.get(0).getId()).isEqualTo(submission.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void triggerBuildInstructor_cannotGetLastCommitHash() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        doThrow(EntityNotFoundException.class).when(gitService).getLastCommitHash(any());
        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);
        bambooRequestMockProvider.mockTriggerBuild((ProgrammingExerciseParticipation) participation);
        bambooRequestMockProvider.mockTriggerBuild((ProgrammingExerciseParticipation) participation);
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.NOT_FOUND,
                new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void triggerBuildInstructorTutorForbidden() throws Exception {
        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);

        String url = "/api/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildInstructorStudentForbidden() throws Exception {
        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);

        String url = "/api/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @Timeout(5)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void triggerBuildForExerciseAsInstructor() throws Exception {
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
        exercise = programmingExerciseRepository.save(exercise);
        bambooRequestMockProvider.mockTriggerBuild(firstParticipation);
        bambooRequestMockProvider.mockTriggerBuild(secondParticipation);
        bambooRequestMockProvider.mockTriggerBuild(thirdParticipation);

        // Each trigger build is mocked twice per participation so that we test
        // that no new submission is created on re-trigger
        bambooRequestMockProvider.mockTriggerBuild(firstParticipation);
        bambooRequestMockProvider.mockTriggerBuild(secondParticipation);
        bambooRequestMockProvider.mockTriggerBuild(thirdParticipation);

        String url = "/api/programming-exercises/" + exercise.getId() + "/trigger-instructor-build-all";
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());

        // Note: the participations above have no submissions, so they are not triggered
        // TODO: write another test with participations with submissions and make sure those are actually triggered

        // due to the async function call on the server, we need to wait here until the server has saved the changes
        await().until(() -> !programmingExerciseRepository.findById(exercise.getId()).get().getTestCasesChanged());
        var optionalUpdatedProgrammingExercise = programmingExerciseRepository.findById(exercise.getId());
        assertThat(optionalUpdatedProgrammingExercise).isPresent();
        ProgrammingExercise updatedProgrammingExercise = optionalUpdatedProgrammingExercise.get();
        assertThat(updatedProgrammingExercise.getTestCasesChanged()).isFalse();
        verify(groupNotificationService, times(1)).notifyEditorAndInstructorGroupAboutExerciseUpdate(updatedProgrammingExercise,
                Constants.TEST_CASES_CHANGED_RUN_COMPLETED_NOTIFICATION);
        verify(websocketMessagingService, times(1)).sendMessage("/topic/programming-exercises/" + exercise.getId() + "/test-cases-changed", false);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void triggerBuildForExerciseEditorForbidden() throws Exception {
        String url = "/api/programming-exercises/" + 1L + "/trigger-instructor-build-all";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void triggerBuildForExerciseTutorForbidden() throws Exception {
        String url = "/api/programming-exercises/" + 1L + "/trigger-instructor-build-all";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildForExerciseStudentForbidden() throws Exception {
        String url = "/api/programming-exercises/" + 1L + "/trigger-instructor-build-all";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void triggerBuildForParticipationsInstructorEmpty() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        String login1 = "student1";
        String login2 = "student2";
        String login3 = "student3";
        ProgrammingExerciseStudentParticipation participation1 = database.addStudentParticipationForProgrammingExercise(exercise, login1);
        database.addStudentParticipationForProgrammingExercise(exercise, login2);
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
        String url = "/api/programming-exercises/" + exercise.getId() + "/trigger-instructor-build";
        request.postWithoutLocation(url, participationsToTrigger, HttpStatus.OK, new HttpHeaders());
        request.postWithoutLocation(url, participationsToTrigger, HttpStatus.OK, new HttpHeaders());

        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        // Note: the participations above have no submissions, so the builds will not be triggered
        assertThat(submissions).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void triggerBuildForParticipationsInstructorParticipationsEmpty() throws Exception {
        String url = "/api/programming-exercises/" + exercise.getId() + "/trigger-instructor-build";
        request.postWithoutLocation(url, List.of(), HttpStatus.BAD_REQUEST, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void triggerBuildForParticipationsEditorForbidden() throws Exception {
        String url = "/api/programming-exercises/" + 1L + "/trigger-instructor-build";
        request.postWithoutLocation(url, new ArrayList<>(), HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void triggerBuildForParticipationsTutorForbidden() throws Exception {
        String url = "/api/programming-exercises/" + 1L + "/trigger-instructor-build";
        request.postWithoutLocation(url, new ArrayList<>(), HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildForParticipationsStudentForbidden() throws Exception {
        String url = "/api/programming-exercises/" + 1L + "/trigger-instructor-build";
        request.postWithoutLocation(url, new ArrayList<>(), HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerFailedBuildResultPresentInCIOk() throws Exception {
        var user = database.getUserByLogin("student1");
        var submission = new ProgrammingSubmission();
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(4));
        submission.setSubmitted(true);
        submission.setCommitHash(TestConstants.COMMIT_HASH_STRING);
        submission.setType(SubmissionType.MANUAL);
        submission = database.addProgrammingSubmission(exercise, submission, "student1");
        var optionalParticipation = programmingExerciseStudentParticipationRepository.findById(submission.getParticipation().getId());
        assertThat(optionalParticipation).isPresent();
        final var participation = optionalParticipation.get();
        bambooRequestMockProvider.enableMockingOfRequests();
        var buildPlan = new BambooBuildPlanDTO(true, false);
        bambooRequestMockProvider.mockGetBuildPlan(participation.getBuildPlanId(), buildPlan, false);
        // Mock again because we call the trigger request two times
        bambooRequestMockProvider.mockGetBuildPlan(participation.getBuildPlanId(), buildPlan, false);

        String url = "/api" + Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH + participation.getId() + "/trigger-failed-build";
        request.postWithoutLocation(url, null, HttpStatus.OK, null);

        verify(messagingTemplate).convertAndSendToUser(user.getLogin(), NEW_SUBMISSION_TOPIC, submission);

        // Perform the request again and make sure no new submission was created
        request.postWithoutLocation(url, null, HttpStatus.OK, null);
        var updatedSubmissions = submissionRepository.findAll();
        assertThat(updatedSubmissions).hasSize(1);
        assertThat(updatedSubmissions.get(0).getId()).isEqualTo(submission.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerFailedBuildSubmissionNotLatestButLastGradedNotFound() throws Exception {
        var participation = createExerciseWithSubmissionAndParticipation();

        String url = "/api" + Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH + participation.getId() + "/trigger-failed-build?lastGraded=true";
        request.postWithoutLocation(url, null, HttpStatus.NOT_FOUND, null);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerFailedBuild_CIException() throws Exception {
        var participation = createExerciseWithSubmissionAndParticipation();
        bambooRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        mockConnectorRequestsForResumeParticipation(exercise, participation.getParticipantIdentifier(), participation.getParticipant().getParticipants(), true);

        doThrow(ContinuousIntegrationException.class).when(continuousIntegrationService).triggerBuild(participation);
        String url = "/api" + Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH + participation.getId() + "/trigger-failed-build";
        request.postWithoutLocation(url, null, HttpStatus.OK, null);
    }

    private ProgrammingExerciseStudentParticipation createExerciseWithSubmissionAndParticipation() {
        var user = database.getUserByLogin("student1");
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        exercise = programmingExerciseRepository.save(exercise);
        var submission = new ProgrammingSubmission();
        submission.setType(SubmissionType.MANUAL);
        submission = database.addProgrammingSubmission(exercise, submission, user.getLogin());
        var optionalParticipation = programmingExerciseStudentParticipationRepository.findById(submission.getParticipation().getId());
        assertThat(optionalParticipation).isPresent();
        var participation = optionalParticipation.get();
        participation.setBuildPlanId(null);
        participation = programmingExerciseStudentParticipationRepository.save(participation);
        return participation;
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerFailedBuildForbiddenParticipationAccess() throws Exception {
        String login = "student2";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);

        String url = "/api/programming-submissions/" + participation.getId() + "/trigger-failed-build";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerFailedBuildEmptyLatestPendingSubmission() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());

        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);
        bambooRequestMockProvider.mockTriggerBuild((ProgrammingExerciseParticipation) participation);

        String url = "/api/programming-submissions/" + participation.getId() + "/trigger-failed-build";
        request.postWithoutLocation(url, null, HttpStatus.NOT_FOUND, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerFailedBuildSubmissionNotFound() throws Exception {
        String login = "student1";
        Course course = database.addCourseWithDifferentModelingExercises();
        ModelingExercise classExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        ModelingSubmission modelingSubmission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/empty-class-diagram.json"),
                true);
        modelingSubmission = database.addModelingSubmission(classExercise, modelingSubmission, login);

        String url = "/api/programming-submissions/" + modelingSubmission.getParticipation().getId() + "/trigger-failed-build";
        request.postWithoutLocation(url, null, HttpStatus.NOT_FOUND, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getAllProgrammingSubmissionsAsUserForbidden() throws Exception {
        request.get("/api/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testNotifyPush_invalidParticipation() throws Exception {
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation = studentParticipationRepository.save(studentParticipation);

        String url = "/api/programming-submissions/" + studentParticipation.getId();
        request.post(url, "test", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testNotifyPush_cannotGetLastCommitDetails() throws Exception {
        var participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        doThrow(ContinuousIntegrationException.class).when(versionControlService).getLastCommitDetails(any());
        String url = "/api/programming-submissions/" + participation.getId();
        request.post(url, "test", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testNotifyPush_commitIsDifferentBranch() throws Exception {
        var participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");

        Commit mockCommit = mock(Commit.class);
        doReturn(mockCommit).when(versionControlService).getLastCommitDetails(any());
        doReturn("branch").when(versionControlService).getDefaultBranchOfRepository(any());
        doReturn("another-branch").when(mockCommit).getBranch();

        String url = "/api/programming-submissions/" + participation.getId();
        request.postWithoutLocation(url, "test", HttpStatus.OK, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testNotifyPush_isSetupCommit() throws Exception {
        var participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");

        Commit mockCommit = mock(Commit.class);
        doReturn(mockCommit).when(versionControlService).getLastCommitDetails(any());
        doReturn("default-branch").when(versionControlService).getDefaultBranchOfRepository(any());

        doReturn("default-branch").when(mockCommit).getBranch();
        doReturn(artemisGitName).when(mockCommit).getAuthorName();
        doReturn(artemisGitEmail).when(mockCommit).getAuthorEmail();
        doReturn(SETUP_COMMIT_MESSAGE).when(mockCommit).getMessage();

        String url = "/api/programming-submissions/" + participation.getId();
        request.postWithoutLocation(url, "test", HttpStatus.OK, new HttpHeaders());

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAllProgrammingSubmissionsAsInstructorAllSubmissionsReturned() throws Exception {
        final var submissions = new ArrayList<ProgrammingSubmission>();
        for (int i = 1; i < 4; i++) {
            final var submission = ModelFactory.generateProgrammingSubmission(true);
            submissions.add(submission);
            database.addProgrammingSubmission(exercise, submission, "student" + i);
        }

        String url = "/api/exercises/" + exercise.getId() + "/programming-submissions";
        final var responseSubmissions = request.getList(url, HttpStatus.OK, ProgrammingSubmission.class);

        assertThat(responseSubmissions).containsExactly(submissions.toArray(new ProgrammingSubmission[0]));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getAllProgrammingSubmissionsAssessedByTutorAllSubmissionsReturned() throws Exception {
        database.addProgrammingSubmission(exercise, ModelFactory.generateProgrammingSubmission(true), "student1");
        var assessedSubmission = ModelFactory.generateProgrammingSubmission(true);
        assessedSubmission = database.addProgrammingSubmission(exercise, assessedSubmission, "student2");
        final var tutor = database.getUserByLogin("tutor1");
        database.addResultToSubmission(assessedSubmission, AssessmentType.SEMI_AUTOMATIC, null);
        database.addResultToSubmission(assessedSubmission, AssessmentType.AUTOMATIC, null);
        database.addResultToSubmission(assessedSubmission, AssessmentType.SEMI_AUTOMATIC, tutor);

        final var paramMap = new LinkedMultiValueMap<String, String>();
        paramMap.add("assessedByTutor", "true");

        String url = "/api/exercises/" + exercise.getId() + "/programming-submissions";
        final var responseSubmissions = request.getList(url, HttpStatus.OK, ProgrammingSubmission.class, paramMap);

        assertThat(responseSubmissions).containsExactly(assessedSubmission);
        assertThat(responseSubmissions.get(0).getResults()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getProgrammingSubmissionWithoutAssessmentAsTutorWithOneAvailableReturnsSubmission() throws Exception {
        String login = "student1";
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        final var submission = database.addProgrammingSubmission(exercise, ModelFactory.generateProgrammingSubmission(true), login);
        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null);

        String url = "/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment";
        final var responseSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);

        assertThat(responseSubmission).isEqualTo(submission);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testLockAndGetProgrammingSubmissionWithManualResult() throws Exception {
        String login = "student1";
        database.addGradingInstructionsToExercise(exercise);
        programmingExerciseRepository.save(exercise);

        ProgrammingSubmission submission = ModelFactory.generateProgrammingSubmission(true);
        submission = database.addProgrammingSubmission(exercise, submission, login);
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise = programmingExerciseRepository.save(exercise);
        database.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));
        Result result = database.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now().minusHours(1).minusMinutes(30),
                programmingExerciseStudentParticipation);

        result.setSubmission(submission);
        submission.addResult(result);
        submission.setParticipation(programmingExerciseStudentParticipation);
        submission = submissionRepository.save(submission);
        var submissions = submissionRepository.findAll();

        String url = "/api/programming-submissions/" + submission.getId() + "/lock";
        var storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);

        // Make sure no new submissions are created
        var latestSubmissions = submissionRepository.findAll();
        assertThat(submissions).hasSameSizeAs(latestSubmissions);

        // Check that grading instructions are loaded
        ProgrammingExercise exercise = (ProgrammingExercise) storedSubmission.getParticipation().getExercise();
        assertThat(exercise.getGradingCriteria().get(0).getStructuredGradingInstructions()).hasSize(1);
        assertThat(exercise.getGradingCriteria().get(1).getStructuredGradingInstructions()).hasSize(3);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testLockAndGetProgrammingSubmissionLessManualResultsThanCorrectionRoundWithoutAutomaticResult() throws Exception {

        ProgrammingSubmission submission = ModelFactory.generateProgrammingSubmission(true);
        submission = database.addProgrammingSubmission(exercise, submission, "student1");
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise = programmingExerciseRepository.save(exercise);
        database.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        submission.setParticipation(programmingExerciseStudentParticipation);
        submission = submissionRepository.save(submission);

        // Make sure that there are no results on the current submission
        assertThat(submission.getLatestResult()).isNull();

        String url = "/api/programming-submissions/" + submission.getId() + "/lock";
        var storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);

        // Make sure that the stored submission has a semi-automatic assessment by tutor 1
        assertThat(storedSubmission.getLatestResult()).isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(storedSubmission.getLatestResult().getAssessor().getLogin()).isEqualTo("tutor1");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testLockAndGetProgrammingSubmissionLessManualResultsThanCorrectionRoundWithAutomaticResult() throws Exception {

        ProgrammingSubmission submission = ModelFactory.generateProgrammingSubmission(true);
        submission = database.addProgrammingSubmission(exercise, submission, "student1");
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise = programmingExerciseRepository.save(exercise);
        database.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        submission.setParticipation(programmingExerciseStudentParticipation);
        submission = submissionRepository.save(submission);

        Result result = database.addResultToParticipation(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusHours(1).minusMinutes(30), programmingExerciseStudentParticipation);

        result.setSubmission(submission);
        submission.addResult(result);
        submission.setParticipation(programmingExerciseStudentParticipation);
        submission = submissionRepository.save(submission);

        // Make sure that there is one automatic result on the current submission
        assertThat(submission.getLatestResult()).isNotNull();
        assertThat(submission.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);

        String url = "/api/programming-submissions/" + submission.getId() + "/lock?correction-round=0";
        var storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);

        // Make sure that the stored submission has a latest manual assessment by tutor 1
        assertThat(storedSubmission.getLatestResult()).isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(storedSubmission.getLatestResult().getAssessor().getLogin()).isEqualTo("tutor1");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testLockAndGetProgrammingSubmissionWithoutManualResult() throws Exception {
        var result = database.addResultToParticipation(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusHours(1).minusMinutes(30), programmingExerciseStudentParticipation);
        var submission = database.addProgrammingSubmissionToResultAndParticipation(result, programmingExerciseStudentParticipation, "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d");
        exercise.setAssessmentType(AssessmentType.AUTOMATIC);
        exercise = programmingExerciseRepository.save(exercise);
        database.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));
        var submissions = submissionRepository.findAll();

        String url = "/api/programming-submissions/" + submission.getId() + "/lock";
        request.get(url, HttpStatus.FORBIDDEN, Participation.class);

        // Make sure no new submissions are created
        var latestSubmissions = submissionRepository.findAll();
        assertThat(submissions).hasSameSizeAs(latestSubmissions);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetProgrammingSubmissionWithoutAssessment() throws Exception {
        String login = "student1";
        ProgrammingSubmission submission = ModelFactory.generateProgrammingSubmission(true);
        submission = database.addProgrammingSubmission(exercise, submission, login);
        database.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        String url = "/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment";
        ProgrammingSubmission storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);

        // set dates to UTC and round to milliseconds for comparison
        submission.setSubmissionDate(ZonedDateTime.ofInstant(submission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        storedSubmission.setSubmissionDate(ZonedDateTime.ofInstant(storedSubmission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(storedSubmission).as("submission was found").isEqualToIgnoringGivenFields(submission, "result");
        assertThat(storedSubmission.getLatestResult()).as("result is not set").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetProgrammingSubmissionWithoutAssessmentLockSubmission() throws Exception {
        database.addGradingInstructionsToExercise(exercise);
        programmingExerciseRepository.save(exercise);
        User user = database.getUserByLogin("tutor1");
        var newResult = database.addResultToParticipation(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusHours(2), programmingExerciseStudentParticipation);
        programmingExerciseStudentParticipation.addResult(newResult);
        var submission = database.addProgrammingSubmissionToResultAndParticipation(newResult, programmingExerciseStudentParticipation, "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d");

        database.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        String url = "/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment?lock=true";
        ProgrammingSubmission storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);

        assertThat(storedSubmission.getLatestResult()).as("result is set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        var automaticResults = storedSubmission.getLatestResult().getFeedbacks().stream().filter(feedback -> feedback.getType() == FeedbackType.AUTOMATIC).toList();
        assertThat(storedSubmission.getLatestResult().getFeedbacks()).hasSameSizeAs(automaticResults);
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        assertThat(submission.getLatestResult()).isNotNull();

        // Make sure no new submissions are created
        var latestSubmissions = submissionRepository.findAll();
        assertThat(latestSubmissions).hasSize(1);
        assertThat(latestSubmissions.get(0).getId()).isEqualTo(submission.getId());

        // Check that grading instructions are loaded
        ProgrammingExercise exercise = (ProgrammingExercise) storedSubmission.getParticipation().getExercise();
        assertThat(exercise.getGradingCriteria().get(0).getStructuredGradingInstructions()).hasSize(1);
        assertThat(exercise.getGradingCriteria().get(1).getStructuredGradingInstructions()).hasSize(3);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetModelSubmissionWithoutAssessmentTestLockLimit() throws Exception {
        createTenLockedSubmissionsForExercise("tutor1");
        database.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        String url = "/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment";
        request.get(url, HttpStatus.BAD_REQUEST, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getProgrammingSubmissionWithoutAssessmentDueDateNotPassedYet() throws Exception {
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        final var submission = database.addProgrammingSubmission(exercise, ModelFactory.generateProgrammingSubmission(true), "student1");
        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null);

        String url = "/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment";
        request.get(url, HttpStatus.FORBIDDEN, String.class);
    }

    /**
     * Checks that submissions for a participation with an individual due date are not shown to tutors before this due date has passed.
     * @param isIndividualDueDateInFuture if the due date is in the future, the submission should not be shown. Otherwise, it should be shown.
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetProgrammingSubmissionWithoutAssessmentWithIndividualDueDate(boolean isIndividualDueDateInFuture) throws Exception {
        // exercise due date in the past
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);

        final var submission = database.addProgrammingSubmission(exercise, ModelFactory.generateProgrammingSubmission(true), "student1");
        if (isIndividualDueDateInFuture) {
            submission.getParticipation().setIndividualDueDate(ZonedDateTime.now().plusDays(1));
        }
        else {
            submission.getParticipation().setIndividualDueDate(ZonedDateTime.now().minusDays(1));
        }
        programmingExerciseStudentParticipationRepository.save((ProgrammingExerciseStudentParticipation) submission.getParticipation());
        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null);

        String url = "/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment";

        if (isIndividualDueDateInFuture) {
            // the submission should not be returned as the due date is in the future
            request.get(url, HttpStatus.FORBIDDEN, String.class);
        }
        else {
            ProgrammingSubmission storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);
            assertThat(storedSubmission.getId()).isEqualTo(submission.getId());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetProgrammingSubmissionWithoutAssessmentNothingAvailable(boolean lock) throws Exception {
        exercise.setDueDate(ZonedDateTime.now().minusDays(2));
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        var submission = ModelFactory.generateProgrammingSubmission(true);
        submission = database.addProgrammingSubmission(exercise, submission, "student1");
        final var tutor = database.getUserByLogin("tutor1");
        database.addResultToSubmission(submission, AssessmentType.SEMI_AUTOMATIC, tutor);

        String url = "/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment?lock=" + lock;

        var storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);
        assertThat(storedSubmission).isNull();
    }

    private void createTenLockedSubmissionsForExercise(String assessor) {
        ProgrammingSubmission submission;
        for (int i = 1; i < 11; i++) {
            submission = ModelFactory.generateProgrammingSubmission(true);
            database.addProgrammingSubmissionWithResultAndAssessor(exercise, submission, "student" + i, assessor, AssessmentType.SEMI_AUTOMATIC, false);
        }
    }
}
