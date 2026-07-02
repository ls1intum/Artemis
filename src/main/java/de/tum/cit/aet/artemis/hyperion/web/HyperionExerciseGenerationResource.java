package de.tum.cit.aet.artemis.hyperion.web;

import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.exercisegeneration.orchestration.ExerciseGenerationJobService;
import de.tum.cit.aet.artemis.hyperion.exercisegeneration.persistence.ExerciseAdaptationRevertService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionReviewCommentContextRendererService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for Hyperion's agentic whole-exercise generation and adaptation.
 * <p>
 * A single endpoint and a single engine drive both {@code GENERATE} and {@code ADAPT} — the client picks the mode explicitly (never inferred from the exercise's contents). The
 * agent produces or revises a complete, verified exercise (problem statement plus all repositories) and it is saved only after the differential oracle has verified it. Progress
 * streams over the websocket topic {@code /topic/hyperion/exercise-generation/jobs/{jobId}}; a run is a multi-minute async job addressed by the returned {@code jobId}.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@RestController
@RequestMapping("api/hyperion/")
public class HyperionExerciseGenerationResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionExerciseGenerationResource.class);

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ExerciseGenerationJobService jobService;

    private final AgentSystemPromptService agentSystemPromptService;

    private final HyperionReviewCommentContextRendererService reviewCommentContextRenderer;

    private final ExerciseAdaptationRevertService adaptationRevertService;

    public HyperionExerciseGenerationResource(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository, ExerciseGenerationJobService jobService,
            AgentSystemPromptService agentSystemPromptService, HyperionReviewCommentContextRendererService reviewCommentContextRenderer,
            ExerciseAdaptationRevertService adaptationRevertService) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.jobService = jobService;
        this.agentSystemPromptService = agentSystemPromptService;
        this.reviewCommentContextRenderer = reviewCommentContextRenderer;
        this.adaptationRevertService = adaptationRevertService;
    }

    /**
     * POST programming-exercises/{exerciseId}/generate-exercise : starts an agentic whole-exercise generation/adaptation run in the request's explicit mode.
     *
     * @param exerciseId the programming exercise id
     * @param request    the request holding the explicit mode and the optional prompt / selected feedback threads
     * @return 202 Accepted with the started job id the client uses to reattach and stream progress; 409 if a run is already active for the exercise
     */
    @PostMapping("programming-exercises/{exerciseId}/generate-exercise")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ExerciseGenerationJobStartDTO> generateExercise(@PathVariable long exerciseId, @Valid @RequestBody ExerciseGenerationRequestDTO request) {
        log.debug("REST request to run agentic exercise generation ({}) for exercise [{}]", request.effectiveMode(), exerciseId);
        validateSelectedFeedbackThreadIds(request.selectedFeedbackThreadIds());
        ProgrammingExercise exercise = loadExercise(exerciseId);
        ProgrammingLanguage language = exercise.getProgrammingLanguage();
        if (!agentSystemPromptService.isGenerationSupported(language)) {
            // Fail clearly instead of running and producing a result the differential oracle cannot verify (best-effort/no-profile languages).
            throw new BadRequestAlertException("Whole-exercise generation is not available for programming language '" + language
                    + "': only languages whose test reports the verifier can parse are supported.", ENTITY_NAME, "unsupportedGenerationLanguage");
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        String prompt = withSelectedFeedback(agentSystemPromptService.resolvePrompt(request, exercise), exerciseId, request);
        String jobId = jobService.startJob(user, exercise, prompt, request.effectiveMode());
        log.info("Started agentic exercise generation job [{}] ({}) for exercise [{}]", jobId, request.effectiveMode(), exerciseId);
        return ResponseEntity.accepted().body(new ExerciseGenerationJobStartDTO(jobId));
    }

    /**
     * GET programming-exercises/generation/supported-languages : the languages Artemis Intelligence offers for one-click whole-exercise generation (the oracle-verifiable set).
     * <p>
     * Not exercise-scoped, so guarded by the global least-privilege role that can create exercises ({@link EnforceAtLeastEditor}). The client consumes this instead of mirroring
     * the set by hand.
     *
     * @return the supported programming languages
     */
    @GetMapping("programming-exercises/generation/supported-languages")
    @EnforceAtLeastEditor
    public ResponseEntity<List<ProgrammingLanguage>> getSupportedGenerationLanguages() {
        log.debug("REST request to get the Hyperion generation-supported programming languages");
        return ResponseEntity.ok(agentSystemPromptService.supportedGenerationLanguages().stream().sorted().toList());
    }

    /**
     * GET programming-exercises/{exerciseId}/generate-exercise/status : returns the caller's current or most-recent run for the exercise (id, whether it is still running, and the
     * transcript so far), so a client that (re)loads the page can replay the progress and reattach to a live run. Returns 204 when there is nothing to show.
     *
     * @param exerciseId the programming exercise id
     * @return the run status with the replayable transcript, or 204 if none is retained for the caller
     */
    @GetMapping("programming-exercises/{exerciseId}/generate-exercise/status")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ExerciseGenerationStatusDTO> getExerciseGenerationStatus(@PathVariable long exerciseId) {
        log.debug("REST request to get the agentic exercise generation status for exercise [{}]", exerciseId);
        ProgrammingExercise exercise = loadExercise(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        return jobService.getStatus(user, exercise).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * DELETE programming-exercises/{exerciseId}/generate-exercise/jobs/{jobId} : requests cooperative cancellation of a running generation job.
     *
     * @param exerciseId the programming exercise id
     * @param jobId      the job id to cancel
     * @return 200 if a matching active job owned by the caller was marked for cancellation, 404 otherwise
     */
    @DeleteMapping("programming-exercises/{exerciseId}/generate-exercise/jobs/{jobId}")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<Void> cancelExerciseGeneration(@PathVariable long exerciseId, @PathVariable String jobId) {
        log.debug("REST request to cancel agentic exercise generation job [{}] for exercise [{}]", jobId, exerciseId);
        // Only the instructor who started the job may cancel it (the jobId is observable, so course scope alone is not enough — see requestCancellation).
        User user = userRepository.getUserWithGroupsAndAuthorities();
        boolean cancelled = jobService.requestCancellation(exerciseId, jobId, user);
        return cancelled ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    /**
     * POST programming-exercises/{exerciseId}/generate-exercise/revert-adaptation : reverts the most recent in-place adaptation of the exercise, resetting its template/solution/tests
     * repositories back to the commit state captured at the start of that adaptation run. The deliberately simple alternative to a staging workflow.
     *
     * @param exerciseId the programming exercise id
     * @return 200 if an adaptation baseline was found and reverted; 404 if there is nothing to revert (no retained baseline)
     */
    @PostMapping("programming-exercises/{exerciseId}/generate-exercise/revert-adaptation")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<Void> revertAdaptation(@PathVariable long exerciseId) {
        log.debug("REST request to revert the last agentic adaptation of exercise [{}]", exerciseId);
        ProgrammingExercise exercise = loadExercise(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        return adaptationRevertService.revert(exercise, user).map(result -> ResponseEntity.ok().<Void>build()).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * For an {@link GenerationMode#ADAPT} run, folds the instructor's selected review-comment threads into the brief so the agent addresses exactly that feedback; a GENERATE run or
     * an adapt with no selected/active threads returns the prompt unchanged.
     *
     * @param basePrompt the resolved brief
     * @param exerciseId the exercise id
     * @param request    the generation request (mode + selected feedback thread ids)
     * @return the prompt with the rendered feedback appended when applicable
     */
    private String withSelectedFeedback(String basePrompt, long exerciseId, ExerciseGenerationRequestDTO request) {
        if (request.effectiveMode() != GenerationMode.ADAPT || request.selectedFeedbackThreadIds() == null || request.selectedFeedbackThreadIds().isEmpty()) {
            return basePrompt;
        }
        String feedback = reviewCommentContextRenderer.renderWholeExerciseSelectedFeedback(exerciseId, request.selectedFeedbackThreadIds());
        return feedback == null || feedback.isBlank() ? basePrompt : basePrompt + "\n\n" + feedback;
    }

    private ProgrammingExercise loadExercise(long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        if (exercise.getBuildConfig() == null) {
            throw new BadRequestAlertException("Exercise must have a build configuration for generation", ENTITY_NAME, "missingBuildConfig");
        }
        return exercise;
    }

    /**
     * Rejects a malformed set of selected review-comment thread ids early (the count is already capped by {@code @Size} on the DTO; this rejects null/non-positive ids). The ids
     * survive from the legacy code-generation contract because the adapt flow addresses selected feedback threads.
     *
     * @param selectedFeedbackThreadIds the ids to validate (may be {@code null} / empty)
     */
    private void validateSelectedFeedbackThreadIds(List<Long> selectedFeedbackThreadIds) {
        if (selectedFeedbackThreadIds == null) {
            return;
        }
        boolean hasInvalidThreadId = selectedFeedbackThreadIds.stream().anyMatch(threadId -> threadId == null || threadId <= 0);
        if (hasInvalidThreadId) {
            throw new BadRequestAlertException("Selected feedback thread ids must be positive", ENTITY_NAME, "invalidSelectedFeedbackThreadIds");
        }
    }
}
