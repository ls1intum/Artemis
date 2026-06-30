package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisStruggleInterventionRequestDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleInterventionEventDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;

/**
 * Capstone end-to-end round-trip test (Task 16) for the proactive struggle-intervention feature. Boots the
 * Spring context and exercises the whole Artemis slice against the HTTP-mocked Pyris: the exercise-keyed
 * trigger endpoint ships the live code + signal to Pyris (the mock captures + asserts the execution DTO), then
 * a Pyris-style status callback drives the decision path. The contract:
 * <ol>
 * <li>live code (the uncommitted {@code src/Sum.java}) + the struggle signal reach Pyris,</li>
 * <li>an {@code active} decision above the confidence threshold lazily creates the exercise session, persists a
 * {@link IrisMessageOrigin#PROACTIVE_STRUGGLE}-origin LLM message, and pushes a per-user {@code active} event
 * (sessionId set, confidence 0.85) on {@code /topic/iris/struggle-intervention},</li>
 * <li>a trailing duplicate callback for the same run is rejected (403, idempotency),</li>
 * <li>an {@code ambient} decision (after unify-persistence, spec §7) also persists a
 * {@link IrisMessageOrigin#PROACTIVE_STRUGGLE} message into the shared session and pushes an {@code ambient} event
 * carrying that session id (confidence 0.7), without a live bubble push.</li>
 * </ol>
 */
class IrisStruggleInterventionRoundTripTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "strugglee2e";

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() throws GitAPIException, IOException, URISyntaxException {
        // Seed an opted-in student1 (UserFactory defaults every generated user to CLOUD_AI, so this is enough
        // for the server-side AI opt-in gate). Re-assert CLOUD_AI defensively.
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student1.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        student1.setSelectedLLMUsageTimestamp(ZonedDateTime.now().minusDays(1));
        userTestRepository.save(student1);

        // Programming exercise + local VC repositories, mirroring PyrisEventSystemIntegrationTest so that
        // toPyrisSubmissionDTO can read the (committed) repository contents off-thread.
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = exercise.getProjectKey();
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);
        exercise.setTestRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(exercise);
        exercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exercise.getId()).orElseThrow();

        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = exercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
        SolutionProgrammingExerciseParticipation solutionParticipation = exercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        String assignmentRepositorySlug = projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1";

        // A participation WITH a submission for student1 — this is what makes latestSubmission(...) non-empty so
        // the live code (the uncommitted src/Sum.java merged on top) is actually shipped to Pyris.
        ProgrammingExerciseStudentParticipation studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        studentParticipation.setRepositoryUri(String.format(localVCBaseUri + "/git/%s/%s.git", projectKey, assignmentRepositorySlug));
        studentParticipation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(studentParticipation);

        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, projectKey.toLowerCase() + "-tests");
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionRepositorySlug);
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, assignmentRepositorySlug);
        localVCLocalCITestService.verifyRepositoryFoldersExist(exercise, localVCBasePath);

        createSubmission(studentParticipation);

        activateIrisFor(course);
        activateIrisFor(exercise);

        // activateIrisFor leaves proactive struggle OFF (the §13 default); this end-to-end run needs it ON.
        var courseSettings = irisSettingsService.getSettingsForCourse(course);
        irisSettingsService.updateCourseSettings(course.getId(),
                IrisCourseSettings.of(courseSettings.enabled(), courseSettings.customInstructions(), courseSettings.variant(), courseSettings.rateLimit(), true), true);
    }

    private void createSubmission(ProgrammingExerciseStudentParticipation studentParticipation) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setBuildFailed(false);
        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission.setSubmissionDate(ZonedDateTime.now());
        submissionRepository.saveAndFlush(submission);
    }

    private long exerciseId() {
        return exercise.getId();
    }

    private long studentId() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void activeDecision_createsSessionPersistsTaggedMessageAndPushes_andTrailingCallbackIsRejected() throws Exception {
        AtomicReference<String> runId = new AtomicReference<>();
        irisRequestMockProvider.mockStruggleInterventionResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            assertThat(dto.struggleSignal().alert().primaryBoundary()).isEqualTo("FM");
            assertThat(dto.programmingExerciseSubmission().repository()).containsKey("src/Sum.java");   // live code shipped
            runId.set(dto.settings().authenticationToken());
        });

        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(540, "FM", List.of("FM"), 0.72, "armed", false, false),
                List.of(new PyrisStruggleSignalDTO.TickDTO(530, 0.6, 0.7)), List.of(new PyrisStruggleSignalDTO.ComponentDTO("feedbackViewing", 0.8)), 540);
        var body = new IrisStruggleInterventionRequestDTO(signal, Map.of("src/Sum.java", "class Sum {}"), null, null, null, null);
        request.postWithoutResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention", body, HttpStatus.ACCEPTED);
        await().atMost(5, TimeUnit.SECONDS).until(() -> runId.get() != null);

        var terminalStage = new PyrisStageDTO("Thinking", 10, PyrisStageState.DONE, null, false, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Have you checked the empty-list case?", "active", 0.85, "FM", List.of(terminalStage), List.of(), null, null,
                null, null, null, null, null, null);
        sendStruggleStatus(runId.get(), update, HttpStatus.OK);

        // The active path lazily CREATED the exercise session and persisted a proactive-tagged LLM message into it.
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var session = irisChatSessionRepository
                    .findLatestByEntityIdAndChatModeAndUserIdWithMessages(exerciseId(), IrisChatMode.PROGRAMMING_EXERCISE_CHAT, studentId(), Pageable.ofSize(1)).stream()
                    .findFirst().orElseThrow();
            assertThat(session.getMessages()).anyMatch(m -> m.getOrigin() == IrisMessageOrigin.PROACTIVE_STRUGGLE);
        });
        // ...and a per-user 'active' event WAS pushed on the per-user struggle topic (sessionId set so the client opens/fetches).
        ArgumentCaptor<Object> activePayload = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService, timeout(5000)).sendMessageToUser(eq(TEST_PREFIX + "student1"), eq("/topic/iris/struggle-intervention"), activePayload.capture());
        assertThat(activePayload.getValue()).isInstanceOf(StruggleInterventionEventDTO.class);
        var activeEvent = (StruggleInterventionEventDTO) activePayload.getValue();
        assertThat(activeEvent.action()).isEqualTo("active");
        assertThat(activeEvent.sessionId()).isNotNull();
        assertThat(activeEvent.confidence()).isEqualTo(0.85);

        // Trailing duplicate callback (job already removed) → 403.
        sendStruggleStatus(runId.get(), update, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ambientDecision_persistsTaggedMessageAndPushesEventWithSession() throws Exception {
        AtomicReference<String> runId = new AtomicReference<>();
        irisRequestMockProvider.mockStruggleInterventionResponse(dto -> runId.set(dto.settings().authenticationToken()));

        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(540, "STATE", List.of("STATE"), 0.65, "armed", false, false), List.of(),
                List.of(new PyrisStruggleSignalDTO.ComponentDTO("regionPersistence", 0.7)), 540);
        var body = new IrisStruggleInterventionRequestDTO(signal, Map.of("src/Sum.java", "class Sum {}"), null, null, null, null);
        request.postWithoutResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention", body, HttpStatus.ACCEPTED);
        await().atMost(5, TimeUnit.SECONDS).until(() -> runId.get() != null);

        var terminalStage = new PyrisStageDTO("Thinking", 10, PyrisStageState.DONE, null, false, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Step back and re-check the logic.", "ambient", 0.7, "STATE", List.of(terminalStage), List.of(), null, null, null,
                null, null, null, null, null);
        sendStruggleStatus(runId.get(), update, HttpStatus.OK);

        // unify-persistence (spec §7): ambient now persists an origin-tagged LLM message into the shared exercise session.
        AtomicReference<Long> savedSessionId = new AtomicReference<>();
        AtomicReference<Long> savedMessageId = new AtomicReference<>();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var session = irisChatSessionRepository
                    .findLatestByEntityIdAndChatModeAndUserIdWithMessages(exerciseId(), IrisChatMode.PROGRAMMING_EXERCISE_CHAT, studentId(), Pageable.ofSize(1)).stream()
                    .findFirst().orElseThrow();
            var proactive = session.getMessages().stream().filter(m -> m.getOrigin() == IrisMessageOrigin.PROACTIVE_STRUGGLE).findFirst().orElseThrow();
            savedSessionId.set(session.getId());
            savedMessageId.set(proactive.getId());
        });

        // ...and a per-user 'ambient' event WAS pushed on the per-user topic, carrying the SAVED message's session +
        // message ids (so a later slice can open/reveal exactly that message). The "no live bubble push" negative is
        // locked down at the unit layer in IrisStruggleInterventionDecisionTest (verify(...never()).sendMessage).
        ArgumentCaptor<Object> ambientPayload = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService, timeout(5000)).sendMessageToUser(eq(TEST_PREFIX + "student1"), eq("/topic/iris/struggle-intervention"), ambientPayload.capture());
        assertThat(ambientPayload.getValue()).isInstanceOf(StruggleInterventionEventDTO.class);
        var ambientEvent = (StruggleInterventionEventDTO) ambientPayload.getValue();
        assertThat(ambientEvent.action()).isEqualTo("ambient");
        assertThat(ambientEvent.sessionId()).isEqualTo(savedSessionId.get());
        assertThat(ambientEvent.messageId()).isEqualTo(savedMessageId.get());
        assertThat(ambientEvent.message()).contains("logic");
        assertThat(ambientEvent.confidence()).isEqualTo(0.7);
    }

    private void sendStruggleStatus(String runId, PyrisStruggleInterventionStatusUpdateDTO update, HttpStatus expected) throws Exception {
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + runId))));
        request.postWithoutResponseBody("/api/iris/internal/pipelines/struggle-intervention/runs/" + runId + "/status", update, expected, headers);
    }
}
