package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyJol;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventType;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisEventProcessingException;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

class PyrisEventSystemIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyriseventsystemintegration";

    @Autowired
    protected IrisSettingsRepository irisSettingsRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    private ProgrammingExercise exercise;

    private Course course;

    private ProgrammingExerciseStudentParticipation studentParticipation;

    private AtomicBoolean pipelineDone;

    private Competency competency;

    @BeforeEach
    void initTestCase() throws GitAPIException, IOException, URISyntaxException {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);

        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student1.setIrisAcceptedTimestamp(ZonedDateTime.now().minusDays(1));
        userTestRepository.save(student1);
        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        student2.setIrisAcceptedTimestamp(ZonedDateTime.now().minusDays(1));
        userTestRepository.save(student2);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        competency = competencyUtilService.createCompetency(course);
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = exercise.getProjectKey();
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);
        exercise.setTestRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(exercise);
        exercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exercise.getId()).orElseThrow();

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

    private Result createSubmission(ProgrammingExerciseStudentParticipation studentParticipation, int score, boolean buildFailed) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setBuildFailed(buildFailed);
        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission = submissionRepository.saveAndFlush(submission);

        Result result = ParticipationFactory.generateResult(true, score);
        result.setSubmission(submission);
        result.completionDate(ZonedDateTime.now());
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        submission.addResult(result);
        submissionRepository.saveAndFlush(submission);

        return resultRepository.save(result);
    }

    private Result createSubmissionWithScore(ProgrammingExerciseStudentParticipation studentParticipation, int score) {
        return createSubmission(studentParticipation, score, false);
    }

    private Result createFailingSubmission(ProgrammingExerciseStudentParticipation studentParticipation) {
        return createSubmission(studentParticipation, 0, true);
    }

    private ProgrammingExerciseStudentParticipation createTeamParticipation(User owner) {
        var team = teamUtilService.addTeamForExercise(exercise, owner);
        var teamParticipation = participationUtilService.addTeamParticipationForProgrammingExercise(exercise, team);
        teamParticipation
                .setRepositoryUri(String.format(localVCBaseUrl + "/git/%s/%s-%s.git", exercise.getProjectKey(), exercise.getProjectKey().toLowerCase(), team.getShortName()));
        return programmingExerciseStudentParticipationRepository.save(teamParticipation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldFireProgressStalledEvent() {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // Create three submissions for the student.
        createSubmissionWithScore(studentParticipation, 40);
        createSubmissionWithScore(studentParticipation, 40);
        var result = createSubmissionWithScore(studentParticipation, 40);
        irisRequestMockProvider.mockProgressStalledEventRunResponse((dto) -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            pipelineDone.set(true);
        });

        pyrisEventService.trigger(new NewResultEvent(result));
        // Wrap the following code into await() to ensure that the pipeline is executed before the test finishes.

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> verify(irisExerciseChatSessionService, times(1)).onNewResult(eq(result)));

        await().atMost(2, TimeUnit.SECONDS).until(() -> pipelineDone.get());

        verify(pyrisPipelineService, times(1)).executeExerciseChatPipeline(eq("default"), eq(Optional.ofNullable((ProgrammingSubmission) result.getSubmission())), eq(exercise),
                eq(irisSession), eq(Optional.of("progress_stalled")));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldFireBuildFailedEvent() {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // Create a failing submissions for the student.
        var result = createFailingSubmission(studentParticipation);
        irisRequestMockProvider.mockBuildFailedRunResponse((dto) -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            pipelineDone.set(true);
        });

        pyrisEventService.trigger(new NewResultEvent(result));

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> verify(irisExerciseChatSessionService, times(1)).onBuildFailure(eq(result)));

        await().atMost(2, TimeUnit.SECONDS).until(() -> pipelineDone.get());

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> verify(pyrisPipelineService, times(1)).executeExerciseChatPipeline(eq("default"),
                eq(Optional.ofNullable((ProgrammingSubmission) result.getSubmission())), eq(exercise), eq(irisSession), eq(Optional.of("build_failed"))));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldFireJolEvent() {
        var irisSession = irisCourseChatSessionService.createSession(course, userUtilService.getUserByLogin(TEST_PREFIX + "student1"), false);
        var jolValue = 3;
        irisRequestMockProvider.mockJolEventRunResponse((dto) -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            pipelineDone.set(true);
        });
        competencyJolService.setJudgementOfLearning(competency.getId(), userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId(), (short) jolValue);

        await().atMost(2, TimeUnit.SECONDS).until(() -> pipelineDone.get());

        verify(irisCourseChatSessionService, times(1)).onJudgementOfLearningSet(any(CompetencyJol.class));
        verify(pyrisPipelineService, times(1)).executeCourseChatPipeline(eq("default"), eq(irisSession), any(CompetencyJol.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireProgressStalledEventWithEventDisabled() {
        // Find settings for the current exercise
        var settings = irisSettingsRepository.findExerciseSettings(exercise.getId()).orElseThrow();
        settings.getIrisChatSettings().setDisabledProactiveEvents(new TreeSet<>(Set.of(IrisEventType.PROGRESS_STALLED.name().toLowerCase())));
        irisSettingsRepository.save(settings);

        createSubmissionWithScore(studentParticipation, 40);
        createSubmissionWithScore(studentParticipation, 40);
        var result = createSubmissionWithScore(studentParticipation, 40);
        verify(pyrisEventService, never()).trigger(new NewResultEvent(result));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireBuildFailedEventWhenEventSettingDisabled() {
        // Find settings for the current exercise
        var settings = irisSettingsRepository.findExerciseSettings(exercise.getId()).orElseThrow();
        settings.getIrisChatSettings().setDisabledProactiveEvents(new TreeSet<>(Set.of(IrisEventType.BUILD_FAILED.name().toLowerCase())));
        irisSettingsRepository.save(settings);

        irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // Create a failing submission for the student.
        var result = createFailingSubmission(studentParticipation);
        // very that the event is not fired
        verify(pyrisEventService, never()).trigger(new NewResultEvent(result));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldShouldNotFireProgressStalledEventWithExistingSuccessfulSubmission() {
        irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        irisRequestMockProvider.mockProgressStalledEventRunResponse((dto) -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            pipelineDone.set(true);
        });
        createSubmissionWithScore(studentParticipation, 100);
        var result = createSubmissionWithScore(studentParticipation, 50);

        pyrisEventService.trigger(new NewResultEvent(result));

        await().atMost(2, TimeUnit.SECONDS);

        result = createSubmissionWithScore(studentParticipation, 50);

        pyrisEventService.trigger(new NewResultEvent(result));
        await().atMost(2, TimeUnit.SECONDS);

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(irisExerciseChatSessionService, times(2)).onNewResult(any(Result.class));
            verify(pyrisPipelineService, never()).executeExerciseChatPipeline(any(), any(), any(), any(), any());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireProgressStalledEventWithLessThanThreeSubmissions() {
        irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // Create two failing submissions for the student.
        createSubmissionWithScore(studentParticipation, 20);
        var result = createSubmissionWithScore(studentParticipation, 20);

        pyrisEventService.trigger(new NewResultEvent(result));

        verify(irisExerciseChatSessionService, timeout(2000).times(1)).onNewResult(any(Result.class));
        verify(pyrisPipelineService, after(2000).never()).executeExerciseChatPipeline(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireProgressStalledEventWithIncreasingScores() {
        irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // Create three submissions with increasing scores for the student.
        createSubmissionWithScore(studentParticipation, 20);
        createSubmissionWithScore(studentParticipation, 30);
        var result = createSubmissionWithScore(studentParticipation, 40);

        pyrisEventService.trigger(new NewResultEvent(result));

        verify(irisExerciseChatSessionService, timeout(2000).times(1)).onNewResult(result);
        verify(pyrisPipelineService, after(2000).never()).executeExerciseChatPipeline(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireBuildFailedEventForTeamSubmission() {
        var owner = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var teamParticipation = createTeamParticipation(owner);
        irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, owner);
        var result = createFailingSubmission(teamParticipation);

        assertThatExceptionOfType(PyrisEventProcessingException.class).isThrownBy(() -> irisExerciseChatSessionService.onBuildFailure(result))
                .withMessageStartingWith("Build failure event is not supported for team participations");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireProgressStalledEventForTeamSubmission() {
        var owner = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var teamParticipation = createTeamParticipation(owner);
        irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, owner);
        createSubmissionWithScore(teamParticipation, 40);
        createSubmissionWithScore(teamParticipation, 40);
        var result = createSubmissionWithScore(teamParticipation, 40);

        assertThatExceptionOfType(PyrisEventProcessingException.class).isThrownBy(() -> irisExerciseChatSessionService.onNewResult(result))
                .withMessageStartingWith("Progress stalled event is not supported for team participations");
    }

}
