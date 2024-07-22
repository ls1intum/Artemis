package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exercise.programming.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisPipelineService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisStatusUpdateService;
import de.tum.in.www1.artemis.service.connectors.pyris.event.PyrisEventService;
import de.tum.in.www1.artemis.service.connectors.pyris.event.SubmissionFailedEvent;
import de.tum.in.www1.artemis.service.connectors.pyris.event.SubmissionSuccessfulEvent;
import de.tum.in.www1.artemis.service.iris.session.IrisCourseChatSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisExerciseChatSessionService;
import de.tum.in.www1.artemis.user.UserUtilService;

public class PyrisEventSystemTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyriseventsystemtest";

    @Autowired
    protected PyrisStatusUpdateService pyrisStatusUpdateService;

    @Autowired
    protected PyrisJobService pyrisJobService;

    @Autowired
    protected IrisSettingsRepository irisSettingsRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @SpyBean
    private PyrisPipelineService pyrisPipelineService;

    @SpyBean
    private IrisExerciseChatSessionService irisExerciseChatSessionService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @SpyBean
    private IrisCourseChatSessionService irisCourseChatSessionService;

    @Autowired
    private PyrisEventService pyrisEventService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private ProgrammingExercise exercise;

    private Course course;

    private ProgrammingExerciseStudentParticipation studentParticipation;

    private AtomicBoolean pipelineDone;

    @BeforeEach
    void initTestCase() throws GitAPIException, IOException, URISyntaxException {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = exercise.getProjectKey();
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);
        exercise.setTestRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(exercise);
        exercise = programmingExerciseRepository.findWithAllParticipationsById(exercise.getId()).orElseThrow();

        // Set the correct repository URIs for the template and the solution participation.
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = exercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
        SolutionProgrammingExerciseParticipation solutionParticipation = exercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        String assignmentRepositorySlug = projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1";

        // Add a participation for student1.
        studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        studentParticipation.setRepositoryUri(String.format(localVCBaseUrl + "/git/%s/%s.git", projectKey, assignmentRepositorySlug));
        studentParticipation.setBranch(defaultBranch);

        programmingExerciseStudentParticipationRepository.save(studentParticipation);

        // Prepare the repositories.
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, projectKey.toLowerCase() + "-tests");
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionRepositorySlug);
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, assignmentRepositorySlug);

        // Check that the repository folders were created in the file system for all base repositories.
        localVCLocalCITestService.verifyRepositoryFoldersExist(exercise, localVCBasePath);

        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);

        pipelineDone = new AtomicBoolean(false);
    }

    private Result createSubmission(ProgrammingExerciseStudentParticipation studentParticipation, boolean successful) {
        // Create a failing submission for the student.
        Submission submission = new ProgrammingSubmission();

        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission = submissionRepository.saveAndFlush(submission);

        Result result = ParticipationFactory.generateResult(true, successful ? 100 : 10);
        result.setParticipation(studentParticipation);
        result.setSubmission(submission);
        result.completionDate(ZonedDateTime.now());
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        submission.addResult(result);
        submissionRepository.saveAndFlush(submission);

        return resultRepository.save(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldFireSubmissionSuccessfulEvent() {
        var irisSession = irisCourseChatSessionService.createSession(course, userUtilService.getUserByLogin(TEST_PREFIX + "student1"), false);
        var result = createSubmission(studentParticipation, true);
        irisRequestMockProvider.mockSubmissionSuccessfulEventRunResponse((dto) -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            pipelineDone.set(true);
        });

        pyrisEventService.trigger(new SubmissionSuccessfulEvent(result));

        verify(irisCourseChatSessionService, times(1)).onSubmissionSuccess(eq(result));

        await().atMost(2, TimeUnit.SECONDS).until(() -> pipelineDone.get());

        verify(pyrisPipelineService, times(1)).executeCourseChatPipeline(eq("submission_successful"), eq(irisSession), eq(exercise));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldFireSubmissionFailedEvent() {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // Create three failing submissions for the student.
        createSubmission(studentParticipation, false);
        createSubmission(studentParticipation, false);
        var result = createSubmission(studentParticipation, false);
        irisRequestMockProvider.mockSubmissionFailedEventRunResponse((dto) -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            pipelineDone.set(true);
        });

        pyrisEventService.trigger(new SubmissionFailedEvent(result));
        verify(irisExerciseChatSessionService, times(1)).onSubmissionFailure(eq(result));

        await().atMost(2, TimeUnit.SECONDS).until(() -> pipelineDone.get());

        verify(pyrisPipelineService, times(1)).executeExerciseChatPipeline(eq("submission_failed"), eq(Optional.ofNullable((ProgrammingSubmission) result.getSubmission())),
                eq(exercise), eq(irisSession));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldFireJolEvent() {
        // TODO: Event service should successfully fire JOL event after JOL value is set
        // Steps to test:
        // Student sets JOL value
        // The event service should fire the JOL event
        // Check if the appropriate function is called in the irisCourseChatSessionService
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldHandleUnsupportedEventException() {
        // TODO: Event service should throw UnsupportedOperationException if the event is not supported
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldFireSubmissionSuccessfulEventWithEventDisabled() {
        // TODO: Event service should not fire submission successful event if the event is disabled
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireSubmissionFailedEventWhenEventSettingDisabled() {
        // TODO: Event service should not fire submission failed event if the event is disabled
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireJolEventWhenEventSettingDisabled() {
        // TODO: Event service should not fire JOL event if the event is disabled
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldFireSubmissionSuccessfulEventOnlyOnceWithMultipleSuccessfulSubmissions() {
        // TODO: Event service should fire submission successful event only once even after multiple successful submissions
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireSubmissionFailedEventWithLessThanThreeFailedSubmissions() {
        // TODO: Event service should not fire submission failed event if the number of failed submissions is less than three
    }

}
