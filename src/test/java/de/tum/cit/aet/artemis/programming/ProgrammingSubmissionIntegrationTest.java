package de.tum.cit.aet.artemis.programming;

import static de.tum.cit.aet.artemis.core.config.Constants.NEW_SUBMISSION_TOPIC;
import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.HalfSecond;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.icl.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

class ProgrammingSubmissionIntegrationTest extends AbstractProgrammingIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "programmingsubmission";

    private ProgrammingExercise exercise;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    @Autowired
    private LocalVCLocalCITestService localVCLocalCITestService;

    private final Map<String, String> participationCommitHashes = new HashMap<>();

    private final List<LocalRepository> createdRepos = new ArrayList<>();

    @BeforeEach
    void init() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 10, 2, 1, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        RepositoryExportTestUtil.createAndWireBaseRepositories(localVCLocalCITestService, exercise);
        exercise = programmingExerciseRepository.save(exercise);
        exercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId()).orElseThrow();
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(exercise);
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(exercise);
        participationUtilService.addProgrammingParticipationWithResultForExercise(exercise, TEST_PREFIX + "student1");

        Result result = participationUtilService.addProgrammingParticipationWithResultForExercise(exercise, TEST_PREFIX + "student1");
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) result.getSubmission().getParticipation();

        seedRepositoryForParticipation(participation, "Student1Seed.java");

        exercise.setTestCasesChanged(true);
        programmingExerciseRepository.save(exercise);

        programmingExerciseStudentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student2");
        seedRepositoryForParticipation(programmingExerciseStudentParticipation, "Student2Seed.java");
    }

    @AfterEach
    void tearDown() throws Exception {
        RepositoryExportTestUtil.cleanupTrackedRepositories();
        RepositoryExportTestUtil.resetRepos(createdRepos.toArray(LocalRepository[]::new));
        createdRepos.clear();
        jenkinsRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void triggerBuildStudent() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests();

        String login = TEST_PREFIX + "student2";
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) participationUtilService
                .addStudentParticipationForProgrammingExercise(exercise, login);
        seedRepositoryForParticipation(participation, "ManualTrigger.java");
        final var programmingExerciseParticipation = ((ProgrammingExerciseParticipation) participation);
        jenkinsRequestMockProvider.mockTriggerBuild(programmingExerciseParticipation.getProgrammingExercise().getProjectKey(), programmingExerciseParticipation.getBuildPlanId(),
                false);

        String url = "/api/programming/programming-submissions/" + participation.getId() + "/trigger-build";
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());

        List<ProgrammingSubmission> submissions = submissionRepository.findAllByParticipationIdWithResults(participation.getId());
        assertThat(submissions).hasSize(1);

        ProgrammingSubmission submission = submissions.getFirst();
        var optionalSubmission = submissionRepository.findWithEagerResultsById(submission.getId());
        assertThat(optionalSubmission).isPresent();
        assertThat(optionalSubmission.get().getLatestResult()).isNull();
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(submission.getType()).isEqualTo(SubmissionType.MANUAL);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggerBuildStudentSubmissionNotFound() throws Exception {
        String login = TEST_PREFIX + "student1";
        Course course = modelingExerciseUtilService.addCourseWithDifferentModelingExercises();
        ModelingExercise classExercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        ModelingSubmission modelingSubmission = ParticipationFactory
                .generateModelingSubmission(TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-class-diagram.json"), true);
        modelingSubmission = modelingExerciseUtilService.addModelingSubmission(classExercise, modelingSubmission, login);

        String url = "/api/programming/programming-submissions/" + modelingSubmission.getParticipation().getId() + "/trigger-build";
        request.postWithoutLocation(url, null, HttpStatus.NOT_FOUND, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerBuildInstructor() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests();
        String login = TEST_PREFIX + "student2";
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) participationUtilService
                .addStudentParticipationForProgrammingExercise(exercise, login);
        seedRepositoryForParticipation(participation, "InstructorTrigger.java");
        final var programmingExerciseParticipation = ((ProgrammingExerciseParticipation) participation);
        jenkinsRequestMockProvider.mockTriggerBuild(programmingExerciseParticipation.getProgrammingExercise().getProjectKey(), programmingExerciseParticipation.getBuildPlanId(),
                false);
        jenkinsRequestMockProvider.mockTriggerBuild(programmingExerciseParticipation.getProgrammingExercise().getProjectKey(), programmingExerciseParticipation.getBuildPlanId(),
                false);
        request.postWithoutLocation("/api/programming/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.OK,
                new HttpHeaders());

        List<ProgrammingSubmission> submissions = submissionRepository.findAllByParticipationIdWithResults(participation.getId());
        assertThat(submissions).hasSize(1);

        ProgrammingSubmission submission = submissions.getFirst();
        var optionalSubmission = submissionRepository.findWithEagerResultsById(submission.getId());
        assertThat(optionalSubmission).isPresent();
        assertThat(optionalSubmission.get().getLatestResult()).isNull();
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(submission.getType()).isEqualTo(SubmissionType.INSTRUCTOR);

        // Trigger the call again and make sure that the submission shouldn't be recreated
        request.postWithoutLocation("/api/programming/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.OK,
                new HttpHeaders());
        var updatedSubmissions = submissionRepository.findAllByParticipationIdWithResults(participation.getId());
        assertThat(updatedSubmissions).hasSize(1);
        assertThat(updatedSubmissions.getFirst().getId()).isEqualTo(submission.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerBuildInstructor_cannotGetLastCommitHash() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests();
        doThrow(EntityNotFoundException.class).when(gitServiceSpy).getLastCommitHash(any());
        String login = TEST_PREFIX + "student1";
        StudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login);
        final var programmingExerciseParticipation = ((ProgrammingExerciseParticipation) participation);
        jenkinsRequestMockProvider.mockTriggerBuild(programmingExerciseParticipation.getProgrammingExercise().getProjectKey(), programmingExerciseParticipation.getBuildPlanId(),
                false);
        jenkinsRequestMockProvider.mockTriggerBuild(programmingExerciseParticipation.getProgrammingExercise().getProjectKey(), programmingExerciseParticipation.getBuildPlanId(),
                false);
        request.postWithoutLocation("/api/programming/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.NOT_FOUND,
                new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void triggerBuildInstructorTutorForbidden() throws Exception {
        String login = TEST_PREFIX + "student1";
        StudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login);

        String url = "/api/programming/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggerBuildInstructorStudentForbidden() throws Exception {
        String login = TEST_PREFIX + "student1";
        StudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login);

        String url = "/api/programming/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggerBuildStudentForbidden() throws Exception {
        StudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student2");
        String url = "/api/programming/programming-submissions/" + participation.getId() + "/trigger-build";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerBuildForExerciseAsInstructor() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests();

        String login1 = TEST_PREFIX + "student1";
        String login2 = TEST_PREFIX + "student2";
        String login3 = TEST_PREFIX + "student3";
        final var firstParticipation = (ProgrammingExerciseStudentParticipation) participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login1);
        final var secondParticipation = (ProgrammingExerciseStudentParticipation) participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login2);
        final var thirdParticipation = (ProgrammingExerciseStudentParticipation) participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login3);

        seedRepositoryForParticipation(firstParticipation, "TriggerSeed1.java");
        seedRepositoryForParticipation(secondParticipation, "TriggerSeed2.java");
        seedRepositoryForParticipation(thirdParticipation, "TriggerSeed3.java");

        // Set test cases changed to true; after the build run it should be false;
        exercise.setTestCasesChanged(true);
        exercise = programmingExerciseRepository.save(exercise);
        final var firstProgrammingExerciseParticipation = ((ProgrammingExerciseParticipation) firstParticipation);
        jenkinsRequestMockProvider.mockTriggerBuild(firstProgrammingExerciseParticipation.getProgrammingExercise().getProjectKey(),
                firstProgrammingExerciseParticipation.getBuildPlanId(), false);
        final var secondProgrammingExerciseParticipation = ((ProgrammingExerciseParticipation) secondParticipation);
        jenkinsRequestMockProvider.mockTriggerBuild(secondProgrammingExerciseParticipation.getProgrammingExercise().getProjectKey(),
                secondProgrammingExerciseParticipation.getBuildPlanId(), false);
        final var thirdProgrammingExerciseParticipation = ((ProgrammingExerciseParticipation) thirdParticipation);
        jenkinsRequestMockProvider.mockTriggerBuild(thirdProgrammingExerciseParticipation.getProgrammingExercise().getProjectKey(),
                thirdProgrammingExerciseParticipation.getBuildPlanId(), false);

        // Each trigger build is mocked twice per participation so that we test
        // that no new submission is created on re-trigger
        jenkinsRequestMockProvider.mockTriggerBuild(firstProgrammingExerciseParticipation.getProgrammingExercise().getProjectKey(),
                firstProgrammingExerciseParticipation.getBuildPlanId(), false);
        jenkinsRequestMockProvider.mockTriggerBuild(secondProgrammingExerciseParticipation.getProgrammingExercise().getProjectKey(),
                secondProgrammingExerciseParticipation.getBuildPlanId(), false);
        jenkinsRequestMockProvider.mockTriggerBuild(thirdProgrammingExerciseParticipation.getProgrammingExercise().getProjectKey(),
                thirdProgrammingExerciseParticipation.getBuildPlanId(), false);

        String url = "/api/programming/programming-exercises/" + exercise.getId() + "/trigger-instructor-build-all";
        request.postWithoutLocation(url, null, HttpStatus.OK, new HttpHeaders());

        // Note: the participations above have no submissions, so they are not triggered
        // TODO: write another test with participations with submissions and make sure those are actually triggered

        // due to the async function call on the server, we need to wait here until the server has saved the changes
        await().until(() -> !programmingExerciseRepository.findById(exercise.getId()).orElseThrow().getTestCasesChanged());
        var optionalUpdatedProgrammingExercise = programmingExerciseRepository.findById(exercise.getId());
        assertThat(optionalUpdatedProgrammingExercise).isPresent();
        ProgrammingExercise updatedProgrammingExercise = optionalUpdatedProgrammingExercise.get();
        assertThat(updatedProgrammingExercise.getTestCasesChanged()).isFalse();
        verify(websocketMessagingService).sendMessage("/topic/programming-exercises/" + exercise.getId() + "/test-cases-changed", false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void triggerBuildForExerciseEditorForbidden() throws Exception {
        String url = "/api/programming/programming-exercises/" + 1L + "/trigger-instructor-build-all";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void triggerBuildForExerciseTutorForbidden() throws Exception {
        String url = "/api/programming/programming-exercises/" + 1L + "/trigger-instructor-build-all";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggerBuildForExerciseStudentForbidden() throws Exception {
        String url = "/api/programming/programming-exercises/" + 1L + "/trigger-instructor-build-all";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerBuildForParticipationsInstructorEmpty() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests();
        String login1 = TEST_PREFIX + "student2";
        String login2 = TEST_PREFIX + "student3";
        String login3 = TEST_PREFIX + "student4";
        ProgrammingExerciseStudentParticipation participation1 = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login1);
        participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login2);
        ProgrammingExerciseStudentParticipation participation3 = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login3);

        // We only trigger two participations here: 1 and 3.
        final var programmingExerciseParticipation1 = ((ProgrammingExerciseParticipation) participation1);
        jenkinsRequestMockProvider.mockTriggerBuild(programmingExerciseParticipation1.getProgrammingExercise().getProjectKey(), programmingExerciseParticipation1.getBuildPlanId(),
                false);
        final var programmingExerciseParticipation3 = ((ProgrammingExerciseParticipation) participation3);
        jenkinsRequestMockProvider.mockTriggerBuild(programmingExerciseParticipation3.getProgrammingExercise().getProjectKey(), programmingExerciseParticipation3.getBuildPlanId(),
                false);

        // Mock again because we call the trigger request two times
        jenkinsRequestMockProvider.mockTriggerBuild(programmingExerciseParticipation1.getProgrammingExercise().getProjectKey(), programmingExerciseParticipation1.getBuildPlanId(),
                false);
        jenkinsRequestMockProvider.mockTriggerBuild(programmingExerciseParticipation3.getProgrammingExercise().getProjectKey(), programmingExerciseParticipation3.getBuildPlanId(),
                false);

        List<Long> participationsToTrigger = new ArrayList<>(Arrays.asList(participation1.getId(), participation3.getId()));

        // Perform a call to trigger-instructor-build-all twice. We want to check that the submissions
        // aren't being re-created.
        String url = "/api/programming/programming-exercises/" + exercise.getId() + "/trigger-instructor-build";
        request.postWithoutLocation(url, participationsToTrigger, HttpStatus.OK, new HttpHeaders());
        request.postWithoutLocation(url, participationsToTrigger, HttpStatus.OK, new HttpHeaders());

        // Note: the participations above have no submissions, so the builds will not be triggered
        assertThat(submissionRepository.findAllByParticipationIdWithResults(participation1.getId())).isEmpty();
        assertThat(submissionRepository.findAllByParticipationIdWithResults(participation3.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggerBuildForParticipationsInstructorParticipationsEmpty() throws Exception {
        String url = "/api/programming/programming-exercises/" + exercise.getId() + "/trigger-instructor-build";
        request.postWithoutLocation(url, List.of(), HttpStatus.BAD_REQUEST, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void triggerBuildForParticipationsEditorForbidden() throws Exception {
        String url = "/api/programming/programming-exercises/" + 1L + "/trigger-instructor-build";
        request.postWithoutLocation(url, new ArrayList<>(), HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void triggerBuildForParticipationsTutorForbidden() throws Exception {
        String url = "/api/programming/programming-exercises/" + 1L + "/trigger-instructor-build";
        request.postWithoutLocation(url, new ArrayList<>(), HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggerBuildForParticipationsStudentForbidden() throws Exception {
        String url = "/api/programming/programming-exercises/" + 1L + "/trigger-instructor-build";
        request.postWithoutLocation(url, new ArrayList<>(), HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void triggerFailedBuildResultPresentInCIOk() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var submission = new ProgrammingSubmission();
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(4));
        submission.setSubmitted(true);
        submission.setCommitHash(participationCommitHashes.get(programmingExerciseStudentParticipation.getParticipantIdentifier()));
        submission.setType(SubmissionType.MANUAL);
        submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, user.getLogin());
        var optionalParticipation = participationRepository.findById(submission.getParticipation().getId());
        assertThat(optionalParticipation).isPresent();
        final var participation = optionalParticipation.get();
        jenkinsRequestMockProvider.enableMockingOfRequests();
        final var programmingExerciseParticipation = ((ProgrammingExerciseParticipation) participation);
        mockGetBuildPlan(programmingExerciseParticipation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId(), true, true, false, false);
        // Mock again because we call the trigger request two times
        mockGetBuildPlan(programmingExerciseParticipation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId(), true, true, false, false);

        String url = "/api/programming/programming-submissions/" + participation.getId() + "/trigger-failed-build";
        request.postWithoutLocation(url, null, HttpStatus.OK, null);

        final Long submissionId = submission.getId();
        verify(websocketMessagingService, timeout(2000)).sendMessageToUser(eq(user.getLogin()), eq(NEW_SUBMISSION_TOPIC),
                argThat(arg -> arg instanceof SubmissionDTO submissionDTO && submissionDTO.id().equals(submissionId)));

        // Perform the request again and make sure no new submission was created
        request.postWithoutLocation(url, null, HttpStatus.OK, null);
        var updatedSubmissions = submissionRepository.findAllByParticipationIdWithResults(participation.getId());
        assertThat(updatedSubmissions).hasSize(1);
        assertThat(updatedSubmissions.getFirst().getId()).isEqualTo(submission.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggerFailedBuildSubmissionNotLatestButLastGradedNotFound() throws Exception {
        var participation = createExerciseWithSubmissionAndParticipation(TEST_PREFIX + "student1");

        String url = "/api/programming/programming-submissions/" + participation.getId() + "/trigger-failed-build?lastGraded=true";
        request.postWithoutLocation(url, null, HttpStatus.NOT_FOUND, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void triggerFailedBuild_CIException() throws Exception {
        var participation = createExerciseWithSubmissionAndParticipation(TEST_PREFIX + "student2");
        jenkinsRequestMockProvider.enableMockingOfRequests();
        mockConnectorRequestsForResumeParticipation(exercise, participation.getParticipantIdentifier(), participation.getParticipant().getParticipants(), true);
        String url = "/api/programming/programming-submissions/" + participation.getId() + "/trigger-failed-build";
        request.postWithoutLocation(url, null, HttpStatus.OK, null);
    }

    private ProgrammingExerciseStudentParticipation createExerciseWithSubmissionAndParticipation(String login) {
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        exercise = programmingExerciseRepository.save(exercise);
        var submission = new ProgrammingSubmission();
        submission.setType(SubmissionType.MANUAL);
        submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, login);
        var optionalParticipation = participationRepository.findById(submission.getParticipation().getId());
        assertThat(optionalParticipation).isPresent();
        var participation = optionalParticipation.get();
        participation.setBuildPlanId(null);
        participation = participationRepository.save(participation);
        return participation;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggerFailedBuildForbiddenParticipationAccess() throws Exception {
        String login = TEST_PREFIX + "student2";
        StudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login);

        String url = "/api/programming/programming-submissions/" + participation.getId() + "/trigger-failed-build";
        request.postWithoutLocation(url, null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggerFailedBuildEmptyLatestPendingSubmission() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests();

        String login = TEST_PREFIX + "student1";
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) participationUtilService
                .addStudentParticipationForProgrammingExercise(exercise, login);
        seedRepositoryForParticipation(participation, "FailedBuild.java");
        final var programmingExerciseParticipation = ((ProgrammingExerciseParticipation) participation);
        jenkinsRequestMockProvider.mockTriggerBuild(programmingExerciseParticipation.getProgrammingExercise().getProjectKey(), programmingExerciseParticipation.getBuildPlanId(),
                false);

        String url = "/api/programming/programming-submissions/" + participation.getId() + "/trigger-failed-build";
        request.postWithoutLocation(url, null, HttpStatus.NOT_FOUND, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggerFailedBuildSubmissionNotFound() throws Exception {
        String login = TEST_PREFIX + "student1";
        Course course = modelingExerciseUtilService.addCourseWithDifferentModelingExercises();
        ModelingExercise classExercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        ModelingSubmission modelingSubmission = ParticipationFactory
                .generateModelingSubmission(TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-class-diagram.json"), true);
        modelingSubmission = modelingExerciseUtilService.addModelingSubmission(classExercise, modelingSubmission, login);

        String url = "/api/programming/programming-submissions/" + modelingSubmission.getParticipation().getId() + "/trigger-failed-build";
        request.postWithoutLocation(url, null, HttpStatus.NOT_FOUND, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllProgrammingSubmissionsAsUserForbidden() throws Exception {
        request.get("/api/programming/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllProgrammingSubmissionsAsInstructorAllSubmissionsReturned() throws Exception {
        final var submissions = new ArrayList<ProgrammingSubmission>();
        for (int i = 1; i < 4; i++) {
            final var submission = ParticipationFactory.generateProgrammingSubmission(true);
            submissions.add(submission);
            programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, TEST_PREFIX + "student" + i);
        }

        String url = "/api/programming/exercises/" + exercise.getId() + "/programming-submissions";
        final var responseSubmissions = request.getList(url, HttpStatus.OK, ProgrammingSubmission.class);

        assertThat(responseSubmissions).containsExactly(submissions.toArray(ProgrammingSubmission[]::new));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllProgrammingSubmissionsAssessedByTutorAllSubmissionsReturned() throws Exception {
        programmingExerciseUtilService.addProgrammingSubmission(exercise, ParticipationFactory.generateProgrammingSubmission(true), TEST_PREFIX + "student1");
        var assessedSubmission = ParticipationFactory.generateProgrammingSubmission(true);
        assessedSubmission = programmingExerciseUtilService.addProgrammingSubmission(exercise, assessedSubmission, TEST_PREFIX + "student2");
        final var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        participationUtilService.addResultToSubmission(assessedSubmission, AssessmentType.SEMI_AUTOMATIC, null);
        participationUtilService.addResultToSubmission(assessedSubmission, AssessmentType.AUTOMATIC, null);
        participationUtilService.addResultToSubmission(assessedSubmission, AssessmentType.SEMI_AUTOMATIC, tutor);

        final var paramMap = new LinkedMultiValueMap<String, String>();
        paramMap.add("assessedByTutor", "true");

        String url = "/api/programming/exercises/" + exercise.getId() + "/programming-submissions";
        final var responseSubmissions = request.getList(url, HttpStatus.OK, ProgrammingSubmission.class, paramMap);

        assertThat(responseSubmissions).containsExactly(assessedSubmission);
        assertThat(responseSubmissions.getFirst().getResults()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getProgrammingSubmissionWithoutAssessmentAsTutorWithOneAvailableReturnsSubmission() throws Exception {
        String login = TEST_PREFIX + "student1";
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        final var submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, ParticipationFactory.generateProgrammingSubmission(true), login);
        participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null);

        String url = "/api/programming/exercises/" + exercise.getId() + "/programming-submission-without-assessment";
        final var responseSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);

        assertThat(responseSubmission).isEqualTo(submission);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testLockAndGetProgrammingSubmissionWithManualResult() throws Exception {
        String login = TEST_PREFIX + "student1";
        exerciseUtilService.addGradingInstructionsToExercise(exercise);
        programmingExerciseRepository.save(exercise);

        ProgrammingSubmission submission = ParticipationFactory.generateProgrammingSubmission(true);
        submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, login);
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise = programmingExerciseRepository.save(exercise);
        exerciseUtilService.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));
        Result result = participationUtilService.addResultToSubmission(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now().minusHours(1).minusMinutes(30), submission);

        result.setSubmission(submission);
        submission.setParticipation(programmingExerciseStudentParticipation);
        submission = submissionRepository.save(submission);
        var submissions = submissionRepository.findAll();

        String url = "/api/programming/programming-submissions/" + submission.getId() + "/lock";
        var storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);

        // Make sure no new submissions are created
        var latestSubmissions = submissionRepository.findAll();
        assertThat(submissions).hasSameSizeAs(latestSubmissions);

        // Check that grading instructions are loaded
        ProgrammingExercise exercise = (ProgrammingExercise) storedSubmission.getParticipation().getExercise();
        assertThat(exercise.getGradingCriteria().stream().map(GradingCriterion::getStructuredGradingInstructions).map(Set::size)).containsExactlyInAnyOrder(1, 1, 3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testLockAndGetProgrammingSubmissionLessManualResultsThanCorrectionRoundWithoutAutomaticResult() throws Exception {

        ProgrammingSubmission submission = ParticipationFactory.generateProgrammingSubmission(true);
        submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, TEST_PREFIX + "student1");
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise = programmingExerciseRepository.save(exercise);
        exerciseUtilService.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        submission.setParticipation(programmingExerciseStudentParticipation);
        submission = submissionRepository.save(submission);

        // Make sure that there are no results on the current submission
        assertThat(submission.getLatestResult()).isNull();

        String url = "/api/programming/programming-submissions/" + submission.getId() + "/lock";
        var storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);

        // Make sure that the stored submission has a semi-automatic assessment by tutor 1
        assertThat(storedSubmission.getLatestResult()).isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(storedSubmission.getLatestResult().getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "tutor1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testLockAndGetProgrammingSubmissionLessManualResultsThanCorrectionRoundWithAutomaticResult() throws Exception {

        ProgrammingSubmission submission = ParticipationFactory.generateProgrammingSubmission(true);
        submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, TEST_PREFIX + "student1");
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise = programmingExerciseRepository.save(exercise);
        exerciseUtilService.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        submission.setParticipation(programmingExerciseStudentParticipation);
        submission = submissionRepository.save(submission);

        Result result = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusHours(1).minusMinutes(30), submission);

        result.setSubmission(submission);
        submission.setParticipation(programmingExerciseStudentParticipation);
        submission = submissionRepository.save(submission);

        // Make sure that there is one automatic result on the current submission
        assertThat(submission.getLatestResult()).isNotNull();
        assertThat(submission.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);

        String url = "/api/programming/programming-submissions/" + submission.getId() + "/lock?correction-round=0";
        var storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);

        // Make sure that the stored submission has a latest manual assessment by tutor 1
        assertThat(storedSubmission.getLatestResult()).isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(storedSubmission.getLatestResult().getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "tutor1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testLockAndGetProgrammingSubmissionWithoutManualResult() throws Exception {
        final var submission = programmingExerciseUtilService.createProgrammingSubmission(programmingExerciseStudentParticipation, true, "1");
        participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null);
        exercise.setAssessmentType(AssessmentType.AUTOMATIC);
        exercise = programmingExerciseRepository.save(exercise);
        exerciseUtilService.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));
        var submissions = submissionRepository.findAll();

        String url = "/api/programming/programming-submissions/" + submission.getId() + "/lock";
        request.get(url, HttpStatus.FORBIDDEN, Participation.class);

        // Make sure no new submissions are created
        var latestSubmissions = submissionRepository.findAll();
        assertThat(submissions).hasSameSizeAs(latestSubmissions);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetProgrammingSubmissionWithoutAssessment() throws Exception {
        String login = TEST_PREFIX + "student1";
        ProgrammingSubmission submission = ParticipationFactory.generateProgrammingSubmission(true);
        submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, login);
        exerciseUtilService.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        String url = "/api/programming/exercises/" + exercise.getId() + "/programming-submission-without-assessment";
        ProgrammingSubmission storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);

        final String[] ignoringFields = { "results", "submissionDate", "participation" };
        assertThat(storedSubmission).as("submission was found").usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(submission);
        assertThat(storedSubmission.getSubmissionDate()).as("submission date is correct").isCloseTo(submission.getSubmissionDate(), HalfSecond());
        assertThat(storedSubmission.getLatestResult()).as("result is not set").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetProgrammingSubmissionWithoutAssessmentLockSubmission() throws Exception {
        exerciseUtilService.addGradingInstructionsToExercise(exercise);
        programmingExerciseRepository.save(exercise);
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");

        final var submission = programmingExerciseUtilService.createProgrammingSubmission(programmingExerciseStudentParticipation, false, "1");
        participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null);

        exerciseUtilService.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        String url = "/api/programming/exercises/" + exercise.getId() + "/programming-submission-without-assessment?lock=true";
        ProgrammingSubmission storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);

        assertThat(storedSubmission.getLatestResult()).as("result is set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        var automaticResults = storedSubmission.getLatestResult().getFeedbacks().stream().filter(feedback -> feedback.getType() == FeedbackType.AUTOMATIC).toList();
        assertThat(storedSubmission.getLatestResult().getFeedbacks()).hasSameSizeAs(automaticResults);
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        assertThat(submission.getLatestResult()).isNotNull();

        // Make sure no new submissions are created
        var latestSubmissions = submissionRepository.findAllByParticipationIdWithResults(programmingExerciseStudentParticipation.getId());
        assertThat(latestSubmissions).hasSize(1);
        assertThat(latestSubmissions.getFirst().getId()).isEqualTo(submission.getId());

        // Check that grading instructions are loaded
        ProgrammingExercise exercise = (ProgrammingExercise) storedSubmission.getParticipation().getExercise();
        assertThat(exercise.getGradingCriteria().stream().map(GradingCriterion::getStructuredGradingInstructions).map(Set::size)).containsExactlyInAnyOrder(1, 1, 3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetModelSubmissionWithoutAssessmentTestLockLimit() throws Exception {
        createTenLockedSubmissionsForExercise(TEST_PREFIX + "tutor1");
        exerciseUtilService.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusHours(1));

        String url = "/api/programming/exercises/" + exercise.getId() + "/programming-submission-without-assessment";
        request.get(url, HttpStatus.BAD_REQUEST, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getProgrammingSubmissionWithoutAssessmentDueDateNotPassedYet() throws Exception {
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        final var submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, ParticipationFactory.generateProgrammingSubmission(true),
                TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null);

        String url = "/api/programming/exercises/" + exercise.getId() + "/programming-submission-without-assessment";
        request.get(url, HttpStatus.FORBIDDEN, String.class);
    }

    /**
     * Checks that submissions for a participation with an individual due date are not shown to tutors before this due date has passed.
     *
     * @param isIndividualDueDateInFuture if the due date is in the future, the submission should not be shown. Otherwise, it should be shown.
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetProgrammingSubmissionWithoutAssessmentWithIndividualDueDate(boolean isIndividualDueDateInFuture) throws Exception {
        // exercise due date in the past
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);

        final var submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, ParticipationFactory.generateProgrammingSubmission(true),
                TEST_PREFIX + "student1");
        if (isIndividualDueDateInFuture) {
            submission.getParticipation().setIndividualDueDate(ZonedDateTime.now().plusDays(1));
        }
        else {
            submission.getParticipation().setIndividualDueDate(ZonedDateTime.now().minusDays(1));
        }
        participationRepository.save((ProgrammingExerciseStudentParticipation) submission.getParticipation());
        participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null);

        String url = "/api/programming/exercises/" + exercise.getId() + "/programming-submission-without-assessment";

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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetProgrammingSubmissionWithoutAssessmentNothingAvailable(boolean lock) throws Exception {
        exercise.setDueDate(ZonedDateTime.now().minusDays(2));
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        var submission = ParticipationFactory.generateProgrammingSubmission(true);
        submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, TEST_PREFIX + "student1");
        final var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        participationUtilService.addResultToSubmission(submission, AssessmentType.SEMI_AUTOMATIC, tutor);

        String url = "/api/programming/exercises/" + exercise.getId() + "/programming-submission-without-assessment?lock=" + lock;

        var storedSubmission = request.get(url, HttpStatus.OK, ProgrammingSubmission.class);
        assertThat(storedSubmission).isNull();
    }

    private void createTenLockedSubmissionsForExercise(String assessor) {
        ProgrammingSubmission submission;
        for (int i = 1; i < 11; i++) {
            submission = ParticipationFactory.generateProgrammingSubmission(true);
            programmingExerciseUtilService.addProgrammingSubmissionWithResultAndAssessor(exercise, submission, TEST_PREFIX + "student" + i, assessor, AssessmentType.SEMI_AUTOMATIC,
                    false);
        }
    }

    private void seedRepositoryForParticipation(ProgrammingExerciseStudentParticipation participation, String filename) throws Exception {
        LocalRepository repo = RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation);
        createdRepos.add(repo);
        RepositoryExportTestUtil.writeFilesAndPush(repo, Map.of(filename, "class %s {}".formatted(filename.replace('.', '_'))), "seed " + filename);
        participationRepository.save(participation);
        var latestCommit = RepositoryExportTestUtil.getLatestCommit(repo);
        participationCommitHashes.put(participation.getParticipantIdentifier(), latestCommit.getName());
    }
}
