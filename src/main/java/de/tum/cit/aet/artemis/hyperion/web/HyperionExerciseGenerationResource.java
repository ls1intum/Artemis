package de.tum.cit.aet.artemis.hyperion.web;

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
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.ExerciseGenerationJobService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for Hyperion's agentic whole-exercise generation and adaptation.
 * <p>
 * This drives a single interactive agent that produces or revises a complete, verified exercise (problem statement plus all repositories) and saves it only after the result has
 * been verified. Progress streams over the websocket topic {@code /topic/hyperion/exercise-generation/jobs/{jobId}}.
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

    public HyperionExerciseGenerationResource(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository, ExerciseGenerationJobService jobService,
            AgentSystemPromptService agentSystemPromptService) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.jobService = jobService;
        this.agentSystemPromptService = agentSystemPromptService;
    }

    /**
     * POST programming-exercises/{exerciseId}/generate-exercise : starts an agentic whole-exercise generation/adaptation run.
     *
     * @param exerciseId the programming exercise id
     * @param request    the request holding the optional prompt
     * @return the started job id
     */
    @PostMapping("programming-exercises/{exerciseId}/generate-exercise")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ExerciseGenerationJobStartDTO> generateExercise(@PathVariable long exerciseId, @Valid @RequestBody ExerciseGenerationRequestDTO request) {
        log.debug("REST request to run agentic exercise generation for exercise [{}]", exerciseId);
        ProgrammingExercise exercise = loadExercise(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        String prompt = agentSystemPromptService.resolvePrompt(request, exercise);
        String jobId = jobService.startJob(user, exercise, prompt);
        log.info("Started agentic exercise generation job [{}] for exercise [{}]", jobId, exerciseId);
        return ResponseEntity.ok(new ExerciseGenerationJobStartDTO(jobId));
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
     * @return 200 if a matching active job was marked for cancellation, 404 otherwise
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

    private ProgrammingExercise loadExercise(long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        if (exercise.getBuildConfig() == null) {
            throw new BadRequestAlertException("Exercise must have a build configuration for generation", ENTITY_NAME, "missingBuildConfig");
        }
        return exercise;
    }
}
