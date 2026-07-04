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

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantJobDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantJobDetailDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantJobStartDTO;

/**
 * REST controller for AI exercise-variant generation — ONE endpoint set for all exercise types
 * (plan Section 5.1); the exercise type is read from the source exercise server-side and the
 * {@code VariantTypeRegistry} resolves the adapters.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@RestController
@RequestMapping("api/hyperion/")
public class HyperionExerciseVariantResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionExerciseVariantResource.class);

    private static final String ENTITY_NAME = "exerciseVariantGeneration";

    // TODO (Sonnet): Inject via constructor (mirror HyperionCodeGenerationResource):
    // UserRepository, ExerciseRepository (generic — the endpoint serves all types),
    // ExerciseVariantJobService, ExerciseVariantTaskService, VariantTypeRegistry.

    /**
     * POST exercises/{exerciseId}/generate-variant : start a variant-generation job.
     *
     * TODO (Sonnet): Implement per plan Section 5.1:
     * 1. Authorization: exercise-scoped @EnforceAtLeastEditorInCourse (via the exercise's course; for exam
     * exercises the check resolves through the exam's course, Section 5.5). Check the existing
     * enforceRoleInExercise annotations for the right variant given a plain exerciseId path variable.
     * 2. Validate: at least one of targetDifficulty/domainText/additionalInstructions non-empty → else 400
     * ("noIntentSelected"). Placement cross-field rules per VariantPlacementDTO TODO; exam exercises force
     * SAME_EXAM_GROUP (Section 5.5).
     * 3. Load the source exercise; registry.isSupported(exercise type) → else 400 with translatable key
     * ("unsupportedType").
     * 4. jobService.startJob(user, exercise, request) (throws 409 ConflictException when a job is already
     * running for the exercise), then taskService.runJobAsync(job, cleanup) — cleanup releases the dedup lock.
     * 5. Return 200 with VariantJobStartDTO(jobId).
     */
    @PostMapping("exercises/{exerciseId}/generate-variant")
    public ResponseEntity<VariantJobStartDTO> generateVariant(@PathVariable long exerciseId, @Valid @RequestBody VariantGenerationRequestDTO request) {
        throw new UnsupportedOperationException("TODO (Sonnet): implement start endpoint (plan Section 5.1)");
    }

    /**
     * GET exercises/{exerciseId}/generate-variant/active : running job for reconnect/dedup (plan Sections 5.1, 5.3
     * point 5 — the wizard calls this on open to re-attach to a running job).
     *
     * TODO (Sonnet): Same authorization as the POST; jobService.getActiveJob(user, exerciseId) → 200 VariantJobDTO
     * or 204 No Content when none (mirror the codegen checkOnly path's noContent()).
     */
    @GetMapping("exercises/{exerciseId}/generate-variant/active")
    public ResponseEntity<VariantJobDTO> getActiveJob(@PathVariable long exerciseId) {
        throw new UnsupportedOperationException("TODO (Sonnet): implement active-job endpoint (plan Section 5.1)");
    }

    /**
     * GET variant-jobs : current user's jobs (running + retained-finished) for the navbar tray (plan Sections 5.1, 5.4).
     *
     * TODO (Sonnet): Authorization: @EnforceAtLeastEditor (user-scoped, no exercise in the path — the service
     * returns ONLY jobs whose initiatorLogin matches the current user, re-checked server-side, Section 5.1).
     * Map via VariantJobDTO.of(...).
     */
    @GetMapping("variant-jobs")
    public ResponseEntity<List<VariantJobDTO>> getJobsOfCurrentUser() {
        throw new UnsupportedOperationException("TODO (Sonnet): implement job-list endpoint (plan Section 5.4)");
    }

    /**
     * GET variant-jobs/{jobId} : full job detail incl. per-phase step outputs for reopening the modal in monitor
     * mode (plan Sections 5.1, 5.4).
     *
     * TODO (Sonnet): jobService.getJob(jobId, login) → 200 VariantJobDetailDTO or 404 when unknown/expired/foreign
     * (do NOT leak whether a foreign job exists — same 404).
     */
    @GetMapping("variant-jobs/{jobId}")
    public ResponseEntity<VariantJobDetailDTO> getJobDetail(@PathVariable String jobId) {
        throw new UnsupportedOperationException("TODO (Sonnet): implement job-detail endpoint (plan Section 5.4)");
    }

    /**
     * DELETE variant-jobs/{jobId} : cooperative cancel (plan Section 5.2).
     *
     * TODO (Sonnet): jobService.requestCancel(jobId, login) — only the initiating user; 409 ConflictException when
     * the job already reached FINALIZING or a terminal phase ("the variant already exists — delete it like any
     * exercise"); 404 when unknown/foreign. Returns 204. The actual cleanup happens in the pipeline when it
     * observes the flag at the next phase boundary / agent-round boundary.
     */
    @DeleteMapping("variant-jobs/{jobId}")
    public ResponseEntity<Void> cancelJob(@PathVariable String jobId) {
        throw new UnsupportedOperationException("TODO (Sonnet): implement cancel endpoint (plan Section 5.2)");
    }
}
