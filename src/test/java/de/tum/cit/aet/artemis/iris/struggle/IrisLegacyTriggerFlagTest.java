package de.tum.cit.aet.artemis.iris.struggle;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;

/**
 * Verifies that the legacy server-side {@code build_failed}/{@code progress_stalled} proactive triggers can be
 * disabled via the {@code artemis.iris.proactive.legacy-build-triggers} flag. When the flag is {@code false},
 * the {@code @EventListener} on {@link de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService} still
 * runs, but its early return suppresses the legacy chat pipeline so exactly one proactive path exists.
 */
class IrisLegacyTriggerFlagTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "legacytrigger";

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private IrisChatSessionUtilService irisChatSessionUtilService;

    private ProgrammingExercise exercise;

    private Course course;

    private ProgrammingExerciseStudentParticipation studentParticipation;

    @BeforeEach
    void initTestCase() throws GitAPIException, IOException, URISyntaxException {
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
        for (User user : users) {
            user.setSelectedLLMUsageTimestamp(ZonedDateTime.parse("2025-12-11T00:00:00Z"));
            user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
            userTestRepository.save(user);
        }

        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student1.setSelectedLLMUsageTimestamp(ZonedDateTime.now().minusDays(1));
        userTestRepository.save(student1);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
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
        studentParticipation.setRepositoryUri(String.format(localVCBaseUri + "/git/%s/%s.git", projectKey, assignmentRepositorySlug));
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

        // Disable the legacy build/progress triggers by setting the @Value flag directly on the shared bean.
        // A @TestPropertySource would fork a second Spring context, which shuts down the named-singleton Hazelcast
        // instance and breaks every later Hazelcast-using test in the slice; ReflectionTestUtils is the established
        // Artemis pattern for toggling a @Value flag without a context fork.
        ReflectionTestUtils.setField(irisChatSessionService, "legacyBuildTriggersEnabled", false);
    }

    @AfterEach
    void restoreLegacyTriggerFlag() {
        // Restore the default so the shared application context is not polluted for subsequent tests.
        ReflectionTestUtils.setField(irisChatSessionService, "legacyBuildTriggersEnabled", true);
    }

    private Result createFailingSubmission(ProgrammingExerciseStudentParticipation studentParticipation) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setBuildFailed(true);
        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        // Ensure deterministic ordering in findAllWithResultsByParticipationIdOrderBySubmissionDateAsc
        submission.setSubmissionDate(ZonedDateTime.now());
        submission = submissionRepository.saveAndFlush(submission);

        Result result = ParticipationFactory.generateResult(true, 0);
        result.setSubmission(submission);
        result.setExerciseId(studentParticipation.getExercise().getId());
        result.completionDate(ZonedDateTime.now());
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        submission.addResult(result);
        submissionRepository.saveAndFlush(submission);

        return resultRepository.save(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void disabledFlag_buildFailedEventDoesNotTriggerLegacyChatPipeline() {
        // Seed the chat session that the legacy build_failed trigger would target if the flag were enabled.
        irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // This pipeline mock would be invoked by the legacy build_failed trigger if the flag were enabled.
        irisRequestMockProvider.mockBuildFailedRunResponse(dto -> {
            // no-op: with the flag off, this callback must never run.
        });

        Result result = createFailingSubmission(studentParticipation);
        var event = new NewResultEvent(result);
        pyrisEventService.trigger(event);

        // The @EventListener still fires; the early return lives inside it, so the listener itself is still invoked.
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> verify(irisChatSessionService, times(1)).handleNewResultEvent(eq(event)));

        // But the legacy chat pipeline must NOT fire. after(2000) gives the async dispatch time to settle so the
        // never() assertion does not race ahead of a pipeline call that would otherwise be in flight (mirrors how
        // PyrisEventSystemIntegrationTest asserts the negative case).
        verify(pyrisPipelineService, after(2000).never()).executeChatPipeline(any(), any(), any(), any());
    }
}
