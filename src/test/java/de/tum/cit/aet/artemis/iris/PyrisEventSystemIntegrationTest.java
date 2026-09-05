package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisPipelineVariant;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisBuildLogEntryDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

class PyrisEventSystemIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyriseventsystemintegration";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private IrisChatSessionUtilService irisChatSessionUtilService;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    private ProgrammingExercise exercise;

    private Course course;

    private ProgrammingExerciseStudentParticipation studentParticipation;

    private AtomicBoolean pipelineDone;

    @BeforeEach
    void initTestCase() throws GitAPIException, IOException, URISyntaxException {
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
        for (User user : users) {
            userUtilService.setAiSelectionDecisionDate(user, ZonedDateTime.parse("2025-12-11T00:00:00Z"));
            userUtilService.setAiSelectionDecision(user, AiSelectionDecision.CLOUD_AI);
            userTestRepository.save(user);
        }

        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        userUtilService.setAiSelectionDecisionDate(student1, ZonedDateTime.now().minusDays(1));
        userTestRepository.save(student1);
        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        userUtilService.setAiSelectionDecisionDate(student2, ZonedDateTime.now().minusDays(1));
        userTestRepository.save(student2);

        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = exercise.getProjectKey();
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);
        exercise.setTestRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(exercise);
        exercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exercise.getId()).orElseThrow();

        // Set the correct repository URIs for the template and the solution participation.
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = exercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
        SolutionProgrammingExerciseParticipation solutionParticipation = exercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        String assignmentRepositorySlug = projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1";

        // Add a participation for student1.
        studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        studentParticipation.setRepositoryUri((localVCBaseUri + "/git/%s/%s.git").formatted(projectKey, assignmentRepositorySlug));
        studentParticipation.setBranch(defaultBranch);

        programmingExerciseStudentParticipationRepository.save(studentParticipation);

        // Prepare the repositories.
        localVCLocalCITestService.createRepository(projectKey, templateRepositorySlug);
        localVCLocalCITestService.createRepository(projectKey, projectKey.toLowerCase() + "-tests");
        localVCLocalCITestService.createRepository(projectKey, solutionRepositorySlug);
        localVCLocalCITestService.createRepository(projectKey, assignmentRepositorySlug);

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
        // Ensure deterministic ordering in findAllWithResultsByParticipationIdOrderBySubmissionDateAsc
        submission.setSubmissionDate(ZonedDateTime.now());
        submission = submissionRepository.saveAndFlush(submission);

        Result result = ParticipationFactory.generateResult(true, score);
        result.setSubmission(submission);
        result.setExerciseId(studentParticipation.getExercise().getId());
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

    /**
     * Creates a failing submission with real build log entries, then reloads it the same way the event thread would see
     * it: {@code results} eagerly fetched, but {@code buildLogEntries} left as an uninitialized lazy proxy, detached from
     * any Hibernate session. Reproduces the off-session state that {@link NewResultEvent}s carry across the
     * {@code CompletableFuture.runAsync} boundary in {@code IrisChatSessionService}.
     */
    private Result createFailingSubmissionWithLazyBuildLogEntries(ProgrammingExerciseStudentParticipation studentParticipation) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setBuildFailed(true);
        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission = submissionRepository.saveAndFlush(submission);

        List<BuildLogEntry> logs = new ArrayList<>();
        logs.add(new BuildLogEntry(ZonedDateTime.now(), "compilation failed: cannot find symbol", submission));
        submission.setBuildLogEntries(new java.util.LinkedHashSet<>(logs));
        submission = submissionRepository.saveAndFlush(submission);

        Result result = ParticipationFactory.generateResult(true, 0);
        result.setSubmission(submission);
        result.setExerciseId(studentParticipation.getExercise().getId());
        result.completionDate(ZonedDateTime.now());
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        submission.addResult(result);
        submissionRepository.saveAndFlush(submission);
        result = resultRepository.save(result);

        ProgrammingSubmission detachedSubmission = programmingSubmissionRepository.findProgrammingSubmissionById(submission.getId()).orElseThrow();
        result.setSubmission(detachedSubmission);
        return result;
    }

    private ProgrammingExerciseStudentParticipation createTeamParticipation(User owner) {
        var team = teamUtilService.addTeamForExercise(exercise, owner);
        var teamParticipation = participationUtilService.addTeamParticipationForProgrammingExercise(exercise, team);
        teamParticipation.setRepositoryUri((localVCBaseUri + "/git/%s/%s-%s.git").formatted(exercise.getProjectKey(), exercise.getProjectKey().toLowerCase(), team.getShortName()));
        return programmingExerciseStudentParticipationRepository.save(teamParticipation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldFireProgressStalledEvent() {
        IrisChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // Create three submissions for the student.
        createSubmissionWithScore(studentParticipation, 40);
        createSubmissionWithScore(studentParticipation, 40);
        Result result = createSubmissionWithScore(studentParticipation, 40);
        irisRequestMockProvider.mockProgressStalledEventRunResponse((dto) -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            pipelineDone.set(true);
        });

        var event = new NewResultEvent(result);
        // Joining the returned future waits for the asynchronous dispatch itself instead of polling for its side
        // effect with a fixed deadline, which timed out whenever the shared task executor was saturated on a loaded
        // CI runner. The event listener runs inside that async task, so the verification below cannot race it.
        pyrisEventService.trigger(event).join();

        verify(irisChatSessionService, times(1)).handleNewResultEvent(eq(event));

        await().atMost(5, TimeUnit.SECONDS).until(() -> pipelineDone.get());

        verify(pyrisPipelineService, times(1)).executeChatPipeline(eq("default"), eq("moderate"), eq(irisSession), eq(Optional.of("progress_stalled")), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldFireBuildFailedEvent() {
        IrisChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // Create a failing submissions for the student.
        Result result = createFailingSubmission(studentParticipation);
        irisRequestMockProvider.mockBuildFailedRunResponse((dto) -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            pipelineDone.set(true);
        });

        var event = new NewResultEvent(result);
        pyrisEventService.trigger(event).join();

        verify(irisChatSessionService, times(1)).handleNewResultEvent(eq(event));

        await().atMost(2, TimeUnit.SECONDS).until(() -> pipelineDone.get());

        verify(pyrisPipelineService, times(1)).executeChatPipeline(eq("default"), eq("moderate"), eq(irisSession), eq(Optional.of("build_failed")), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldFireBuildFailedEventWhenBuildLogEntriesAreNotEagerlyFetched() {
        IrisChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        Result result = createFailingSubmissionWithLazyBuildLogEntries(studentParticipation);
        irisRequestMockProvider.mockBuildFailedRunResponse((dto) -> {
            assertThat(dto.programmingExerciseSubmission()).isNotNull();
            assertThat(dto.programmingExerciseSubmission().buildLogEntries()).extracting(PyrisBuildLogEntryDTO::message).containsExactly("compilation failed: cannot find symbol");
            pipelineDone.set(true);
        });

        var event = new NewResultEvent(result);
        pyrisEventService.trigger(event).join();

        verify(irisChatSessionService, times(1)).handleNewResultEvent(eq(event));

        await().atMost(2, TimeUnit.SECONDS).until(() -> pipelineDone.get());

        verify(pyrisPipelineService, times(1)).executeChatPipeline(eq("default"), eq("moderate"), eq(irisSession), eq(Optional.of("build_failed")), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildFailedFallsBackToCourseSessionAndAppliesExerciseContext() {
        // No exercise chat session exists yet, so the event handler falls back to a fresh empty course session and
        // layers the exercise context on via applyContextChange before running the pipeline. The session handed to
        // the pipeline therefore ends up in PROGRAMMING_EXERCISE_CHAT mode pointing at the exercise.
        Result result = createFailingSubmission(studentParticipation);
        irisRequestMockProvider.mockBuildFailedRunResponse((dto) -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            pipelineDone.set(true);
        });

        var event = new NewResultEvent(result);
        pyrisEventService.trigger(event).join();

        verify(irisChatSessionService, times(1)).handleNewResultEvent(eq(event));
        // No pre-existing session here (unlike the sibling tests above), so the handler first creates the fallback
        // course session and applies the exercise context before the pipeline runs; allow the same 5s budget used
        // for the slower progress-stalled case above instead of the 2s used where a session is already prepared.
        await().atMost(5, TimeUnit.SECONDS).until(() -> pipelineDone.get());

        ArgumentCaptor<IrisChatSession> sessionCaptor = ArgumentCaptor.forClass(IrisChatSession.class);
        verify(pyrisPipelineService, times(1)).executeChatPipeline(eq("default"), eq("moderate"), sessionCaptor.capture(), eq(Optional.of("build_failed")), any());

        IrisChatSession usedSession = sessionCaptor.getValue();
        assertThat(usedSession.getMode()).isEqualTo(IrisChatMode.PROGRAMMING_EXERCISE_CHAT);
        assertThat(usedSession.getEntityId()).isEqualTo(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireProgressStalledEventWhenCourseDisabled() {
        disableIrisFor(course);
        createSubmissionWithScore(studentParticipation, 40);
        createSubmissionWithScore(studentParticipation, 40);
        var result = createSubmissionWithScore(studentParticipation, 40);
        verify(pyrisEventService, never()).trigger(new NewResultEvent(result));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireBuildFailedEventWhenCourseDisabled() {
        disableIrisFor(course);
        irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        Result result = createFailingSubmission(studentParticipation);

        // very that the event is not fired
        verify(pyrisEventService, never()).trigger(new NewResultEvent(result));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldShouldNotFireProgressStalledEventWithExistingSuccessfulSubmission() {
        irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        irisRequestMockProvider.mockProgressStalledEventRunResponse((dto) -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            pipelineDone.set(true);
        });
        createSubmissionWithScore(studentParticipation, 100);
        Result result = createSubmissionWithScore(studentParticipation, 50);

        pyrisEventService.trigger(new NewResultEvent(result)).join();

        result = createSubmissionWithScore(studentParticipation, 50);

        pyrisEventService.trigger(new NewResultEvent(result)).join();

        verify(irisChatSessionService, times(2)).handleNewResultEvent(any(NewResultEvent.class));
        verify(pyrisPipelineService, after(2000).never()).executeChatPipeline(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireProgressStalledEventWithLessThanThreeSubmissions() {
        irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // Create two failing submissions for the student.
        createSubmissionWithScore(studentParticipation, 20);
        var result = createSubmissionWithScore(studentParticipation, 20);

        pyrisEventService.trigger(new NewResultEvent(result)).join();

        verify(irisChatSessionService, times(1)).handleNewResultEvent(any(NewResultEvent.class));
        verify(pyrisPipelineService, after(2000).never()).executeChatPipeline(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireProgressStalledEventWithIncreasingScores() {
        irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // Create three submissions with increasing scores for the student.
        createSubmissionWithScore(studentParticipation, 20);
        createSubmissionWithScore(studentParticipation, 30);
        Result result = createSubmissionWithScore(studentParticipation, 40);

        var event = new NewResultEvent(result);
        pyrisEventService.trigger(event).join();

        verify(irisChatSessionService, times(1)).handleNewResultEvent(event);
        verify(pyrisPipelineService, after(2000).never()).executeChatPipeline(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireBuildFailedEventForTeamSubmission() {
        User owner = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        ProgrammingExerciseStudentParticipation teamParticipation = createTeamParticipation(owner);
        irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise, owner);
        Result result = createFailingSubmission(teamParticipation);
        var event = new NewResultEvent(result);

        pyrisEventService.trigger(event).join();

        verify(irisChatSessionService, times(1)).handleNewResultEvent(event);
        verify(pyrisPipelineService, after(2000).never()).executeChatPipeline(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldNotFireProgressStalledEventForTeamSubmission() {
        User owner = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        ProgrammingExerciseStudentParticipation teamParticipation = createTeamParticipation(owner);
        irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise, owner);
        createSubmissionWithScore(teamParticipation, 40);
        createSubmissionWithScore(teamParticipation, 40);
        Result result = createSubmissionWithScore(teamParticipation, 40);
        var event = new NewResultEvent(result);

        verify(irisChatSessionService, after(2000).never()).handleNewResultEvent(event);
        verify(pyrisPipelineService, after(2000).never()).executeChatPipeline(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCustomInstructionsPassedToExerciseChatPipeline() {
        String testCustomInstructions = "Test custom instructions for the AI model";
        configureCourseSettings(course, testCustomInstructions, IrisPipelineVariant.DEFAULT);

        IrisChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        Result result = createFailingSubmission(studentParticipation);

        irisRequestMockProvider.mockBuildFailedRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            assertThat(dto.customInstructions()).isEqualTo(testCustomInstructions);
            pipelineDone.set(true);
        });

        var event = new NewResultEvent(result);
        pyrisEventService.trigger(event).join();

        verify(irisChatSessionService, times(1)).handleNewResultEvent(eq(event));

        await().atMost(2, TimeUnit.SECONDS).until(() -> pipelineDone.get());

        verify(pyrisPipelineService, times(1)).executeChatPipeline(eq("default"), eq("moderate"), eq(irisSession), eq(Optional.of("build_failed")), any());
    }

}
