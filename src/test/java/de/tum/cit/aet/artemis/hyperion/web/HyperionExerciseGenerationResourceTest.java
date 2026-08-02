package de.tum.cit.aet.artemis.hyperion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.buildagent.service.RemoteInteractiveSandboxClient;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.exception.TooManyRequestsAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.config.HyperionGenerationCapacityHealthIndicator;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationAccountingState;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEffortProfileDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRevertResultDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationUsageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.HyperionReviewCommentContextRendererService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.HyperionEffortProfileService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.HyperionGenerationBudgetService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ExerciseGenerationRevertService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Resource-level unit tests for {@link HyperionExerciseGenerationResource}: the non-LLM contract (202/409/404/204/400 and role gating) with the collaborators mocked. The
 * end-to-end
 * agentic behaviour is covered by the mocked-LLM E2E tests, not here.
 */
class HyperionExerciseGenerationResourceTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Mock
    private GenerationJobService jobService;

    @Mock
    private AgentSystemPromptService agentSystemPromptService;

    @Mock
    private HyperionReviewCommentContextRendererService reviewCommentContextRenderer;

    @Mock
    private ExerciseGenerationRevertService generationRevertService;

    @Mock
    private RemoteInteractiveSandboxClient sandboxClient;

    @Mock
    private HyperionGenerationBudgetService generationBudgetService;

    @Mock
    private HyperionGenerationCapacityHealthIndicator generationCapacityHealthIndicator;

    private HyperionExerciseGenerationResource resource;

    private User testUser;

    private ProgrammingExercise testExercise;

    /** A real resolver over the shipped defaults: the profile feature is inert unless a deployment configures profiles, which is exactly the state these tests exercise. */
    private final HyperionEffortProfileService effortProfileService = new HyperionEffortProfileService(new HyperionAgentProperties(), List.of());

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resource = new HyperionExerciseGenerationResource(userRepository, programmingExerciseRepository, auxiliaryRepositoryRepository, jobService, agentSystemPromptService,
                reviewCommentContextRenderer, generationRevertService, sandboxClient, generationBudgetService, generationCapacityHealthIndicator, effortProfileService);
        when(auxiliaryRepositoryRepository.findByExerciseId(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        when(sandboxClient.hasAvailableGenerationSandboxSlot()).thenReturn(true);
        when(generationBudgetService.reserveGenerationBudget(any(), any(), anyLong())).thenReturn(HyperionGenerationBudgetService.BudgetReservation.none());

        testUser = new User();
        testUser.setLogin("testuser");

        testExercise = new ProgrammingExercise();
        testExercise.setId(1L);
        testExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        testExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        testExercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
    }

    @Test
    void generateExercise_withValidGenerateRequest_returns202AndJobId() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, "Build a bubble sort exercise.", null);
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(agentSystemPromptService.isGenerationSupported(testExercise)).thenReturn(true);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(agentSystemPromptService.resolvePrompt(request, testExercise)).thenReturn("RESOLVED");
        when(jobService.startJob(eq(testUser), eq(testExercise), eq("RESOLVED"), eq(GenerationMode.GENERATE), eq(null), eq("Build a bubble sort exercise."), any()))
                .thenReturn("job-123");

        ResponseEntity<ExerciseGenerationJobStartDTO> response = resource.generateExercise(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("job-123");
        verify(jobService).startJob(eq(testUser), eq(testExercise), eq("RESOLVED"), eq(GenerationMode.GENERATE), eq(null), eq("Build a bubble sort exercise."), any());
    }

    @Test
    void generateExercise_withoutAFreeSandboxSlot_returnsServiceUnavailableBeforeClaimingJob() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, "Build a bubble sort exercise.", null);
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(agentSystemPromptService.isGenerationSupported(testExercise)).thenReturn(true);
        when(sandboxClient.hasAvailableGenerationSandboxSlot()).thenReturn(false);

        assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> resource.generateExercise(1L, request))
                .satisfies(exception -> assertThat(exception.getBody().getProperties()).containsEntry("message", "error.generationCapacityUnavailable"));

        verify(jobService, never()).startJob(any(), any(), any(), any(), any(), any(), any());
        // The rejection must leave a trace on the server: without it the only signal is a bare 503 in the client, and the default of zero sandbox slots is undiagnosable.
        verify(generationCapacityHealthIndicator).warnGenerationRejectedForMissingCapacity();
    }

    @Test
    void generateExercise_whenTokenBudgetExceeded_doesNotClaimJob() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, "Build a bubble sort exercise.", null);
        testUser.setId(7L);
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(agentSystemPromptService.isGenerationSupported(testExercise)).thenReturn(true);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        org.mockito.Mockito.doThrow(new TooManyRequestsAlertException("budget exceeded", "hyperionExerciseGeneration", "generationTokenBudgetExceeded"))
                .when(generationBudgetService).reserveGenerationBudget(eq(7L), any(), anyLong());

        assertThatExceptionOfType(TooManyRequestsAlertException.class).isThrownBy(() -> resource.generateExercise(1L, request));

        verify(jobService, never()).startJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateExercise_withAdaptMode_forwardsModeToJobService() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.ADAPT, "Fix the off-by-one.", List.of(5L, 9L));
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(agentSystemPromptService.isGenerationSupported(testExercise)).thenReturn(true);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(agentSystemPromptService.resolvePrompt(request, testExercise)).thenReturn("RESOLVED");
        when(jobService.startJob(eq(testUser), eq(testExercise), eq("RESOLVED"), eq(GenerationMode.ADAPT), eq(null), eq(null), any())).thenReturn("job-adapt");

        ResponseEntity<ExerciseGenerationJobStartDTO> response = resource.generateExercise(1L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("job-adapt");
        verify(jobService).startJob(eq(testUser), eq(testExercise), eq("RESOLVED"), eq(GenerationMode.ADAPT), eq(null), eq(null), any());
    }

    @Test
    void generateExercise_withAdaptModeAndSelectedFeedback_foldsRenderedFeedbackIntoPrompt() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.ADAPT, "Fix the off-by-one.", List.of(5L, 9L));
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(agentSystemPromptService.isGenerationSupported(testExercise)).thenReturn(true);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(agentSystemPromptService.resolvePrompt(request, testExercise)).thenReturn("RESOLVED");
        when(reviewCommentContextRenderer.renderWholeExerciseSelectedFeedback(1L, List.of(5L, 9L))).thenReturn("FEEDBACK_BLOCK");
        when(jobService.startJob(eq(testUser), eq(testExercise), argThat(prompt -> prompt.contains("RESOLVED") && prompt.contains("FEEDBACK_BLOCK")), eq(GenerationMode.ADAPT),
                eq(null), eq(null), any())).thenReturn("job-adapt-feedback");

        ResponseEntity<ExerciseGenerationJobStartDTO> response = resource.generateExercise(1L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("job-adapt-feedback");
        verify(jobService).startJob(eq(testUser), eq(testExercise), argThat(prompt -> prompt.contains("RESOLVED") && prompt.contains("FEEDBACK_BLOCK")), eq(GenerationMode.ADAPT),
                eq(null), eq(null), any());
    }

    @Test
    void generateExercise_whenPromptRenderingFails_doesNotReserveBudgetOrClaimJob() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.ADAPT, "Fix the off-by-one.", List.of(5L));
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(agentSystemPromptService.isGenerationSupported(testExercise)).thenReturn(true);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(agentSystemPromptService.resolvePrompt(request, testExercise)).thenThrow(new IllegalStateException("prompt rendering failed"));

        assertThatThrownBy(() -> resource.generateExercise(1L, request)).isInstanceOf(IllegalStateException.class).hasMessageContaining("prompt rendering failed");

        verify(generationBudgetService, never()).reserveGenerationBudget(any(), any(), anyLong());
        verify(jobService, never()).startJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void revertExerciseGeneration_whenBaselineExists_returns200() {
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.claimRevertSlot(testUser, 1L)).thenReturn("revert-slot");
        when(generationRevertService.findRevertibleJobId(1L)).thenReturn(Optional.of("adapt-job"));
        when(generationRevertService.revert(eq(testExercise), eq(testUser), any(BooleanSupplier.class)))
                .thenReturn(Optional.of(new ExerciseGenerationRevertService.RevertResult(true, List.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS))));

        ResponseEntity<ExerciseGenerationRevertResultDTO> response = resource.revertExerciseGeneration(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fullyReverted()).isTrue();
        // The Hyperion-facing label (matching the "template"/"solution"/"tests" vocabulary already used by file-change events), not RepositoryType#getName()'s LocalVC-facing
        // "exercise" for TEMPLATE.
        assertThat(response.getBody().revertedRepositories()).containsExactly("template", "solution", "tests");
        assertThat(response.getBody().completedAt()).isNotNull();
        verify(jobService).discardRetainedRun(1L, "adapt-job");
        verify(jobService).clearRevertSlot(1L, "revert-slot");
    }

    @Test
    void revertExerciseGeneration_whenRevertIsPartial_returns409() {
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.claimRevertSlot(testUser, 1L)).thenReturn("revert-slot");
        when(generationRevertService.findRevertibleJobId(1L)).thenReturn(Optional.of("adapt-job"));
        when(generationRevertService.revert(eq(testExercise), eq(testUser), any(BooleanSupplier.class)))
                .thenReturn(Optional.of(new ExerciseGenerationRevertService.RevertResult(false, List.of(RepositoryType.SOLUTION))));

        ResponseEntity<ExerciseGenerationRevertResultDTO> response = resource.revertExerciseGeneration(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fullyReverted()).isFalse();
        verify(jobService, never()).discardRetainedRun(1L, "adapt-job");
        verify(jobService).clearRevertSlot(1L, "revert-slot");
    }

    @Test
    void revertExerciseGeneration_whenNothingToRevert_returns404() {
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.claimRevertSlot(testUser, 1L)).thenReturn("revert-slot");
        when(generationRevertService.revert(eq(testExercise), eq(testUser), any(BooleanSupplier.class))).thenReturn(Optional.empty());

        ResponseEntity<ExerciseGenerationRevertResultDTO> response = resource.revertExerciseGeneration(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(jobService).clearRevertSlot(1L, "revert-slot");
    }

    @Test
    void revertExerciseGeneration_whenMutationSlotIsAlreadyClaimed_returnsConflict() {
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.claimRevertSlot(testUser, 1L))
                .thenThrow(new ConflictException("Exercise generation is already running for this exercise", "hyperionExerciseGeneration", "exerciseGenerationRunning"));

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.revertExerciseGeneration(1L));

        verify(generationRevertService, never()).revert(any(), any(), any());
        verify(jobService, never()).clearRevertSlot(eq(1L), any());
    }

    @Test
    void generateExercise_whenStartJobReportsActiveRun_releasesReservationAndPropagatesConflict() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null);
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(agentSystemPromptService.isGenerationSupported(testExercise)).thenReturn(true);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(agentSystemPromptService.resolvePrompt(request, testExercise)).thenReturn("RESOLVED");
        when(generationBudgetService.reserveGenerationBudget(any(), any(), anyLong())).thenReturn(new HyperionGenerationBudgetService.BudgetReservation("reservation-conflict"));
        when(jobService.startJob(eq(testUser), eq(testExercise), eq("RESOLVED"), eq(GenerationMode.GENERATE), eq("reservation-conflict"), eq(null), any()))
                .thenThrow(new ConflictException("Exercise generation is already running for this exercise", "hyperionExerciseGeneration", "exerciseGenerationRunning"));

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.generateExercise(1L, request));
        verify(jobService).startJob(eq(testUser), eq(testExercise), eq("RESOLVED"), eq(GenerationMode.GENERATE), eq("reservation-conflict"), eq(null), any());
        verify(generationBudgetService).releaseReservation("reservation-conflict");
    }

    @Test
    void generateExercise_whenActiveRunStillOwnsSlot_returnsConflictBeforeSandboxCapacityCheck() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null);
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(agentSystemPromptService.isGenerationSupported(testExercise)).thenReturn(true);
        org.mockito.Mockito.doThrow(new ConflictException("Exercise generation is already running for this exercise", "hyperionExerciseGeneration", "exerciseGenerationRunning"))
                .when(jobService).rejectIfActiveJobCannotBeReclaimed(1L);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.generateExercise(1L, request));

        verify(sandboxClient, never()).hasAvailableGenerationSandboxSlot();
        verify(generationBudgetService, never()).reserveGenerationBudget(any(), any(), anyLong());
        verify(jobService, never()).startJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateExercise_withUnsupportedLanguage_throwsBadRequest() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null);
        testExercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(agentSystemPromptService.isGenerationSupported(testExercise)).thenReturn(false);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> resource.generateExercise(1L, request))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("unsupportedGenerationLanguage"))
                .withMessageContaining("Whole-exercise generation is not available");
        verify(jobService, never()).startJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateExercise_withJavaBlackboxProject_rejectsUnverifiedDejaGnuGrading() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null);
        testExercise.setProjectType(ProjectType.MAVEN_BLACKBOX);
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(agentSystemPromptService.isGenerationSupported(testExercise)).thenReturn(false);

        assertThatThrownBy(() -> resource.generateExercise(1L, request)).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("project type 'MAVEN_BLACKBOX'");
        verify(jobService, never()).startJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateExercise_whenExerciseIsReleased_rejectsLiveMutation() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null);
        testExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));

        assertThatThrownBy(() -> resource.generateExercise(1L, request)).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("unreleased draft");
        verify(jobService, never()).startJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateExercise_whenExerciseHasStudentParticipations_rejectsLiveMutation() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.ADAPT, null, null);
        testExercise.setStudentParticipations(Set.of(mock(StudentParticipation.class)));
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));

        assertThatThrownBy(() -> resource.generateExercise(1L, request)).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("without student participations");
        verify(jobService, never()).startJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateExercise_withMissingBuildConfig_throwsBadRequest() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null);
        testExercise.setBuildConfig(null);
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));

        assertThatThrownBy(() -> resource.generateExercise(1L, request)).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("build configuration");
        verify(jobService, never()).startJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateExercise_withNonPositiveFeedbackThreadId_throwsBadRequest() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.ADAPT, null, List.of(0L));

        assertThatThrownBy(() -> resource.generateExercise(1L, request)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Selected feedback thread ids must be positive");
        verify(jobService, never()).startJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getSupportedGenerationLanguages_returnsSortedSet() {
        // An unordered supported set must be served in a stable (natural enum) order; JAVA precedes PYTHON.
        when(agentSystemPromptService.supportedGenerationLanguages()).thenReturn(Set.of(ProgrammingLanguage.PYTHON, ProgrammingLanguage.JAVA));

        ResponseEntity<List<ProgrammingLanguage>> response = resource.getSupportedGenerationLanguages();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(ProgrammingLanguage.JAVA, ProgrammingLanguage.PYTHON);
    }

    @Test
    void getExerciseGenerationStatus_whenRunIsActive_hidesRevertCapability() {
        ExerciseGenerationStatusDTO status = new ExerciseGenerationStatusDTO("job-42", true, GenerationMode.ADAPT,
                List.of(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "go")), List.of(), false);
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.of(status));
        when(generationRevertService.findRevertibleRun(1L)).thenReturn(Optional.of(new ExerciseGenerationRevertService.RevertibleRun("job-42", GenerationMode.ADAPT)));
        when(jobService.hasActiveJob(1L)).thenReturn(true);

        ResponseEntity<ExerciseGenerationStatusDTO> response = resource.getExerciseGenerationStatus(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("job-42");
        assertThat(response.getBody().running()).isTrue();
        assertThat(response.getBody().mode()).isEqualTo(GenerationMode.ADAPT);
        assertThat(response.getBody().revertAvailable()).isFalse();
    }

    @Test
    void getExerciseGenerationStatus_preservesActiveRunOwnership() {
        ExerciseGenerationStatusDTO status = new ExerciseGenerationStatusDTO("job-42", true, GenerationMode.ADAPT, List.of(), List.of(), false, null, null, false, false);
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.of(status));

        ResponseEntity<ExerciseGenerationStatusDTO> response = resource.getExerciseGenerationStatus(1L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().ownedByCaller()).isFalse();
        assertThat(response.getBody().cancellable()).isFalse();
    }

    @Test
    void getExerciseGenerationStatus_preservesSpecDocumentThroughTheRevertInfoRemap() {
        ExerciseGenerationStatusDTO status = new ExerciseGenerationStatusDTO("job-42", false, GenerationMode.GENERATE, List.of(), List.of(), false, null, null, true, false,
                "# Spec\n## Rules\n- R1");
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.of(status));
        when(generationRevertService.findRevertibleRun(1L)).thenReturn(Optional.empty());

        ResponseEntity<ExerciseGenerationStatusDTO> response = resource.getExerciseGenerationStatus(1L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().specDocument()).isEqualTo("# Spec\n## Rules\n- R1");
    }

    @Test
    void getExerciseGenerationStatusPreservesUsageAndAccountingStateThroughRevertRemap() {
        ExerciseGenerationUsageDTO usage = new ExerciseGenerationUsageDTO(2, 3, 9, 2, 100, 50, 0, false, 0.25, false, List.of("model"), List.of(), false);
        ExerciseGenerationStatusDTO status = new ExerciseGenerationStatusDTO("job-42", false, GenerationMode.GENERATE, List.of(), List.of(), false, null, null, true, false, null,
                usage, ExerciseGenerationAccountingState.INCOMPLETE, null);
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.of(status));

        ExerciseGenerationStatusDTO response = resource.getExerciseGenerationStatus(1L).getBody();

        assertThat(response).isNotNull();
        assertThat(response.usage()).isEqualTo(usage);
        assertThat(response.accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
    }

    @Test
    void getExerciseGenerationStatus_whenOnlyRevertBaselineRemains_returnsRevertCapability() {
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.empty());
        when(generationRevertService.findRevertibleRun(1L)).thenReturn(Optional.of(new ExerciseGenerationRevertService.RevertibleRun("adapt-job", GenerationMode.ADAPT)));

        ResponseEntity<ExerciseGenerationStatusDTO> response = resource.getExerciseGenerationStatus(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("adapt-job");
        assertThat(response.getBody().mode()).isEqualTo(GenerationMode.ADAPT);
        assertThat(response.getBody().revertAvailable()).isTrue();
        assertThat(response.getBody().revertJobId()).isEqualTo("adapt-job");
        assertThat(response.getBody().revertMode()).isEqualTo(GenerationMode.ADAPT);
        assertThat(response.getBody().events()).isEmpty();
        assertThat(response.getBody().fileChanges()).isEmpty();
    }

    @Test
    void getExerciseGenerationStatus_whenOnlyGenerateBaselineRemains_preservesGenerateMode() {
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.empty());
        when(generationRevertService.findRevertibleRun(1L)).thenReturn(Optional.of(new ExerciseGenerationRevertService.RevertibleRun("generate-job", GenerationMode.GENERATE)));

        ResponseEntity<ExerciseGenerationStatusDTO> response = resource.getExerciseGenerationStatus(1L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("generate-job");
        assertThat(response.getBody().mode()).isEqualTo(GenerationMode.GENERATE);
        assertThat(response.getBody().revertAvailable()).isTrue();
    }

    @Test
    void getExerciseGenerationStatus_whenAnotherMutationIsActive_hidesRevertCapability() {
        ExerciseGenerationStatusDTO status = new ExerciseGenerationStatusDTO("new-job", true, GenerationMode.GENERATE, List.of(), List.of(), false);
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.of(status));
        when(generationRevertService.findRevertibleRun(1L)).thenReturn(Optional.of(new ExerciseGenerationRevertService.RevertibleRun("old-adaptation", GenerationMode.ADAPT)));
        when(jobService.hasActiveJob(1L)).thenReturn(true);

        ResponseEntity<ExerciseGenerationStatusDTO> response = resource.getExerciseGenerationStatus(1L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().revertAvailable()).isFalse();
        assertThat(response.getBody().revertJobId()).isNull();
        assertThat(response.getBody().revertMode()).isNull();
    }

    @Test
    void getExerciseGenerationStatus_whenExerciseHasParticipations_hidesRevertCapability() {
        ExerciseGenerationStatusDTO status = new ExerciseGenerationStatusDTO("job-42", false, GenerationMode.ADAPT, List.of(), List.of(), false);
        testExercise.setStudentParticipations(Set.of(mock(StudentParticipation.class)));
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.of(status));
        when(generationRevertService.findRevertibleRun(1L)).thenReturn(Optional.of(new ExerciseGenerationRevertService.RevertibleRun("job-42", GenerationMode.ADAPT)));

        ResponseEntity<ExerciseGenerationStatusDTO> response = resource.getExerciseGenerationStatus(1L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().revertAvailable()).isFalse();
    }

    @Test
    void getExerciseGenerationStatus_whenExerciseIsReleased_hidesRevertCapability() {
        ExerciseGenerationStatusDTO status = new ExerciseGenerationStatusDTO("job-42", false, GenerationMode.ADAPT, List.of(), List.of(), false);
        testExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.of(status));
        when(generationRevertService.findRevertibleRun(1L)).thenReturn(Optional.of(new ExerciseGenerationRevertService.RevertibleRun("job-42", GenerationMode.ADAPT)));

        ResponseEntity<ExerciseGenerationStatusDTO> response = resource.getExerciseGenerationStatus(1L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().revertAvailable()).isFalse();
    }

    @Test
    void getExerciseGenerationStatus_whenNothingRetained_returns204() {
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.empty());
        when(generationRevertService.findRevertibleRun(1L)).thenReturn(Optional.empty());

        ResponseEntity<ExerciseGenerationStatusDTO> response = resource.getExerciseGenerationStatus(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void cancelExerciseGeneration_whenActiveJobOwned_returns200() {
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.requestCancellation(1L, "job-42", testUser)).thenReturn(true);

        ResponseEntity<Void> response = resource.cancelExerciseGeneration(1L, "job-42");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void cancelExerciseGeneration_whenNoMatchingJob_returns404() {
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.requestCancellation(1L, "job-unknown", testUser)).thenReturn(false);

        ResponseEntity<Void> response = resource.cancelExerciseGeneration(1L, "job-unknown");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void endpoints_enforceLeastPrivilegeRoles() throws NoSuchMethodException {
        Method generate = HyperionExerciseGenerationResource.class.getMethod("generateExercise", long.class, ExerciseGenerationRequestDTO.class);
        Method status = HyperionExerciseGenerationResource.class.getMethod("getExerciseGenerationStatus", long.class);
        Method cancel = HyperionExerciseGenerationResource.class.getMethod("cancelExerciseGeneration", long.class, String.class);
        Method revert = HyperionExerciseGenerationResource.class.getMethod("revertExerciseGeneration", long.class);
        Method supported = HyperionExerciseGenerationResource.class.getMethod("getSupportedGenerationLanguages");

        assertThat(generate.getAnnotation(EnforceAtLeastEditorInExercise.class)).isNotNull();
        assertThat(status.getAnnotation(EnforceAtLeastEditorInExercise.class)).isNotNull();
        assertThat(cancel.getAnnotation(EnforceAtLeastEditorInExercise.class)).isNotNull();
        assertThat(revert.getAnnotation(EnforceAtLeastEditorInExercise.class)).isNotNull();
        // The supported-languages endpoint is not exercise-scoped, so it is guarded by the global least-privilege editor role instead.
        assertThat(supported.getAnnotation(EnforceAtLeastEditor.class)).isNotNull();
    }

    /** A resource wired to a deployment that configures two profiles, so the endpoint's set and the accepted set can be compared. */
    private HyperionExerciseGenerationResource resourceWithProfiles() {
        HyperionAgentProperties properties = new HyperionAgentProperties();
        properties.setMaxTokensPerJob(3_000_000L);
        properties.setMaxJobDuration(Duration.ofMinutes(45));
        LinkedHashMap<String, HyperionAgentProperties.EffortProfileProperties> profiles = new LinkedHashMap<>();
        HyperionAgentProperties.EffortProfileProperties draft = new HyperionAgentProperties.EffortProfileProperties();
        draft.setLabel("Quick draft");
        draft.setModel("draft-model");
        draft.setMaxTokensPerJob(600_000L);
        draft.setMaxJobDuration(Duration.ofMinutes(12));
        profiles.put("draft", draft);
        HyperionAgentProperties.EffortProfileProperties standard = new HyperionAgentProperties.EffortProfileProperties();
        standard.setLabel("Standard");
        profiles.put("standard", standard);
        properties.setProfiles(profiles);
        return new HyperionExerciseGenerationResource(userRepository, programmingExerciseRepository, auxiliaryRepositoryRepository, jobService, agentSystemPromptService,
                reviewCommentContextRenderer, generationRevertService, sandboxClient, generationBudgetService, generationCapacityHealthIndicator,
                new HyperionEffortProfileService(properties, List.of()));
    }

    private void stubHappyPath(ExerciseGenerationRequestDTO request) {
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(agentSystemPromptService.isGenerationSupported(testExercise)).thenReturn(true);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(agentSystemPromptService.resolvePrompt(request, testExercise)).thenReturn("RESOLVED");
    }

    @Test
    void getGenerationEffortProfiles_returnsNameAndLabelOnly() {
        List<ExerciseGenerationEffortProfileDTO> profiles = resourceWithProfiles().getGenerationEffortProfiles().getBody();

        assertThat(profiles).containsExactly(new ExerciseGenerationEffortProfileDTO("draft", "Quick draft"), new ExerciseGenerationEffortProfileDTO("standard", "Standard"));
    }

    @Test
    void getGenerationEffortProfiles_withoutConfiguredProfiles_isEmpty() {
        assertThat(resource.getGenerationEffortProfiles().getBody()).isEmpty();
    }

    @Test
    void generateExercise_withAnUnknownEffortProfile_isRejectedBeforeAnythingIsReserved() {
        // Fail closed. A silent fallback is how an instructor gets a surprise bill, and how a comparison between two configurations silently becomes a comparison of one.
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, "brief", null, "thorough", null, null);
        HyperionExerciseGenerationResource profiled = resourceWithProfiles();

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> profiled.generateExercise(1L, request))
                .satisfies(exception -> assertThat(exception.getBody().getProperties()).containsEntry("message", "error.unknownEffortProfile"));

        verify(generationBudgetService, never()).reserveGenerationBudget(any(), any(), anyLong());
        verify(jobService, never()).startJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateExercise_withAnEffortProfile_reservesAndStartsUnderThatProfile() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, "brief", null, "draft", null, null);
        stubHappyPath(request);
        ArgumentCaptor<HyperionGenerationSettings> settings = ArgumentCaptor.forClass(HyperionGenerationSettings.class);

        resourceWithProfiles().generateExercise(1L, request);

        verify(generationBudgetService).reserveGenerationBudget(any(), any(), eq(600_000L));
        verify(jobService).startJob(any(), any(), any(), any(), any(), any(), settings.capture());
        assertThat(settings.getValue().name()).isEqualTo("draft");
        assertThat(settings.getValue().maxJobDuration()).isEqualTo(Duration.ofMinutes(12));
    }

    @Test
    void generateExercise_withRequestedBoundsBelowTheProfile_narrowsBoth() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, "brief", null, "draft", 50_000L, Duration.ofMinutes(5));
        stubHappyPath(request);
        ArgumentCaptor<HyperionGenerationSettings> settings = ArgumentCaptor.forClass(HyperionGenerationSettings.class);

        resourceWithProfiles().generateExercise(1L, request);

        // The reservation is sized to what the run may actually spend, which is the point of allowing the bound at all.
        verify(generationBudgetService).reserveGenerationBudget(any(), any(), eq(50_000L));
        verify(jobService).startJob(any(), any(), any(), any(), any(), any(), settings.capture());
        assertThat(settings.getValue().maxJobDuration()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void generateExercise_withRequestedBoundsAboveTheProfile_clampsInsteadOfWidening() {
        // A request may only tighten. Clamping rather than rejecting means a client never has to know the server's ceiling to construct a valid request.
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, "brief", null, "draft", 900_000_000L, Duration.ofHours(9));
        stubHappyPath(request);
        ArgumentCaptor<HyperionGenerationSettings> settings = ArgumentCaptor.forClass(HyperionGenerationSettings.class);

        resourceWithProfiles().generateExercise(1L, request);

        verify(generationBudgetService).reserveGenerationBudget(any(), any(), eq(600_000L));
        verify(jobService).startJob(any(), any(), any(), any(), any(), any(), settings.capture());
        assertThat(settings.getValue().maxJobDuration()).isEqualTo(Duration.ofMinutes(12));
    }

    @Test
    void generateExercise_withANonPositiveRequestedDuration_isRejected() {
        // Clamping is right for "at most N"; a zero or negative N has no run it could have meant, so it is rejected rather than silently turned into the ceiling.
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, "brief", null, null, null, Duration.ZERO);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> resource.generateExercise(1L, request))
                .satisfies(exception -> assertThat(exception.getBody().getProperties()).containsEntry("message", "error.invalidMaxJobDuration"));

        verify(jobService, never()).startJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateExercise_withoutConfiguredProfiles_stillRejectsANamedProfile() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, "brief", null, "draft", null, null);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> resource.generateExercise(1L, request));
    }

    @Test
    void getExerciseGenerationStatus_carriesTheResolvedEffortProfileThrough() {
        ExerciseGenerationStatusDTO status = new ExerciseGenerationStatusDTO("job-42", false, GenerationMode.GENERATE, List.of(), List.of(), false, null, null, true, false, null,
                null, ExerciseGenerationAccountingState.COMPLETE, "thorough");
        when(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(1L)).thenReturn(Optional.of(testExercise));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.of(status));

        assertThat(resource.getExerciseGenerationStatus(1L).getBody()).isNotNull().extracting(ExerciseGenerationStatusDTO::effortProfile).isEqualTo("thorough");
    }
}
