package de.tum.cit.aet.artemis.hyperion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.exercisegeneration.orchestration.ExerciseGenerationJobService;
import de.tum.cit.aet.artemis.hyperion.exercisegeneration.profile.LanguageGenerationProfile;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Resource-level unit tests for {@link HyperionExerciseGenerationResource}: the non-LLM contract (202/409/404/204/400 and role gating) with the collaborators mocked. The end-to-end
 * agentic behaviour is covered by the authentic GPU E2E harness, not here.
 */
class HyperionExerciseGenerationResourceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Mock
    private ExerciseGenerationJobService jobService;

    @Mock
    private AgentSystemPromptService agentSystemPromptService;

    private HyperionExerciseGenerationResource resource;

    private User testUser;

    private ProgrammingExercise testExercise;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resource = new HyperionExerciseGenerationResource(userRepository, programmingExerciseRepository, jobService, agentSystemPromptService);

        testUser = new User();
        testUser.setLogin("testuser");

        testExercise = new ProgrammingExercise();
        testExercise.setId(1L);
        testExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        testExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
    }

    @Test
    void generateExercise_withValidGenerateRequest_returns202AndJobId() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, "Build a bubble sort exercise.", null);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(agentSystemPromptService.isGenerationSupported(ProgrammingLanguage.JAVA)).thenReturn(true);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(agentSystemPromptService.resolvePrompt(request, testExercise)).thenReturn("RESOLVED");
        when(jobService.startJob(testUser, testExercise, "RESOLVED", GenerationMode.GENERATE)).thenReturn("job-123");

        ResponseEntity<ExerciseGenerationJobStartDTO> response = resource.generateExercise(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("job-123");
        verify(jobService).startJob(testUser, testExercise, "RESOLVED", GenerationMode.GENERATE);
    }

    @Test
    void generateExercise_withAdaptMode_forwardsModeToJobService() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.ADAPT, "Fix the off-by-one.", List.of(5L, 9L));
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(agentSystemPromptService.isGenerationSupported(ProgrammingLanguage.JAVA)).thenReturn(true);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(agentSystemPromptService.resolvePrompt(request, testExercise)).thenReturn("RESOLVED");
        when(jobService.startJob(testUser, testExercise, "RESOLVED", GenerationMode.ADAPT)).thenReturn("job-adapt");

        ResponseEntity<ExerciseGenerationJobStartDTO> response = resource.generateExercise(1L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("job-adapt");
        verify(jobService).startJob(testUser, testExercise, "RESOLVED", GenerationMode.ADAPT);
    }

    @Test
    void generateExercise_withNullMode_defaultsToGenerate() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(null, null, null);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(agentSystemPromptService.isGenerationSupported(ProgrammingLanguage.JAVA)).thenReturn(true);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(agentSystemPromptService.resolvePrompt(request, testExercise)).thenReturn("RESOLVED");
        when(jobService.startJob(testUser, testExercise, "RESOLVED", GenerationMode.GENERATE)).thenReturn("job-default");

        resource.generateExercise(1L, request);

        verify(jobService).startJob(testUser, testExercise, "RESOLVED", GenerationMode.GENERATE);
    }

    @Test
    void generateExercise_whenRunAlreadyActive_propagatesConflict() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(agentSystemPromptService.isGenerationSupported(ProgrammingLanguage.JAVA)).thenReturn(true);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(agentSystemPromptService.resolvePrompt(request, testExercise)).thenReturn("RESOLVED");
        when(jobService.startJob(testUser, testExercise, "RESOLVED", GenerationMode.GENERATE))
                .thenThrow(new ConflictException("Exercise generation is already running for this exercise", "hyperionExerciseGeneration", "exerciseGenerationRunning"));

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.generateExercise(1L, request));
    }

    @Test
    void generateExercise_withUnsupportedLanguage_throwsBadRequest() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null);
        testExercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(agentSystemPromptService.isGenerationSupported(ProgrammingLanguage.PYTHON)).thenReturn(false);

        assertThatThrownBy(() -> resource.generateExercise(1L, request)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Whole-exercise generation is not available");
        verify(jobService, never()).startJob(any(), any(), any(), any());
    }

    @Test
    void generateExercise_withMissingBuildConfig_throwsBadRequest() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null);
        testExercise.setBuildConfig(null);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);

        assertThatThrownBy(() -> resource.generateExercise(1L, request)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("build configuration");
        verify(jobService, never()).startJob(any(), any(), any(), any());
    }

    @Test
    void generateExercise_withNonPositiveFeedbackThreadId_throwsBadRequest() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(GenerationMode.ADAPT, null, List.of(0L));

        assertThatThrownBy(() -> resource.generateExercise(1L, request)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Selected feedback thread ids must be positive");
        verify(jobService, never()).startJob(any(), any(), any(), any());
    }

    @Test
    void getSupportedGenerationLanguages_returnsSortedSet() {
        when(agentSystemPromptService.supportedGenerationLanguages()).thenReturn(Set.of(ProgrammingLanguage.JAVA));

        ResponseEntity<List<ProgrammingLanguage>> response = resource.getSupportedGenerationLanguages();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(ProgrammingLanguage.JAVA);
    }

    @Test
    void getExerciseGenerationStatus_whenRunRetained_returns200WithTranscript() {
        ExerciseGenerationStatusDTO status = new ExerciseGenerationStatusDTO("job-42", true, List.of(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "go")));
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.of(status));

        ResponseEntity<ExerciseGenerationStatusDTO> response = resource.getExerciseGenerationStatus(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("job-42");
        assertThat(response.getBody().running()).isTrue();
    }

    @Test
    void getExerciseGenerationStatus_whenNothingRetained_returns204() {
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(jobService.getStatus(testUser, testExercise)).thenReturn(Optional.empty());

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
        Method supported = HyperionExerciseGenerationResource.class.getMethod("getSupportedGenerationLanguages");

        assertThat(generate.getAnnotation(EnforceAtLeastEditorInExercise.class)).isNotNull();
        assertThat(status.getAnnotation(EnforceAtLeastEditorInExercise.class)).isNotNull();
        assertThat(cancel.getAnnotation(EnforceAtLeastEditorInExercise.class)).isNotNull();
        // The supported-languages endpoint is not exercise-scoped, so it is guarded by the global least-privilege editor role instead.
        assertThat(supported.getAnnotation(EnforceAtLeastEditor.class)).isNotNull();
    }

    @Test
    void supportedGenerationLanguages_areLimitedToJava() {
        // Lock the Java-only production gate at the resource boundary: the resource serves exactly the profile's supported set.
        when(agentSystemPromptService.supportedGenerationLanguages()).thenReturn(LanguageGenerationProfile.supportedLanguages());

        ResponseEntity<List<ProgrammingLanguage>> response = resource.getSupportedGenerationLanguages();

        assertThat(response.getBody()).containsExactly(ProgrammingLanguage.JAVA);
    }
}
