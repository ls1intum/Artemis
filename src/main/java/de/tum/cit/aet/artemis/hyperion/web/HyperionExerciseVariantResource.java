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
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantJobDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantJobDetailDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantPlacementDTO;
import de.tum.cit.aet.artemis.hyperion.service.variants.ExerciseVariantJobService;
import de.tum.cit.aet.artemis.hyperion.service.variants.ExerciseVariantTaskService;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantJob;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantTypeRegistry;

/**
 * REST controller for AI exercise-variant generation — ONE endpoint set for all exercise types
 * (plan Section 5.1); the exercise type is read from the source exercise server-side and the
 * {@link VariantTypeRegistry} resolves the adapters.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@RestController
@RequestMapping("api/hyperion/")
public class HyperionExerciseVariantResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionExerciseVariantResource.class);

    private static final String ENTITY_NAME = "exerciseVariantGeneration";

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final ExerciseVariantJobService jobService;

    private final ExerciseVariantTaskService taskService;

    private final VariantTypeRegistry typeRegistry;

    public HyperionExerciseVariantResource(UserRepository userRepository, ExerciseRepository exerciseRepository, ExerciseVariantJobService jobService,
            ExerciseVariantTaskService taskService, VariantTypeRegistry typeRegistry) {
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.jobService = jobService;
        this.taskService = taskService;
        this.typeRegistry = typeRegistry;
    }

    /**
     * POST exercises/{exerciseId}/generate-variant : start a variant-generation job (plan Section 5.1).
     *
     * @param exerciseId the source exercise id
     * @param request    the wizard request (intents by field presence, placement)
     * @return 200 with the created job id; 400 on missing intent / unsupported type / invalid placement;
     *         409 when a job is already running for the exercise
     */
    @PostMapping("exercises/{exerciseId}/generate-variant")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<VariantJobStartDTO> generateVariant(@PathVariable long exerciseId, @Valid @RequestBody VariantGenerationRequestDTO request) {
        log.debug("REST request to generate a variant for exercise [{}]", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        validateRequest(exercise, request);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        VariantJob job = jobService.startJob(user, exercise, request);
        taskService.runJobAsync(job);
        log.info("Started variant generation job [{}] for exercise [{}]", job.getJobId(), exerciseId);
        return ResponseEntity.ok(new VariantJobStartDTO(job.getJobId()));
    }

    /**
     * GET exercises/{exerciseId}/generate-variant/active : running job for wizard reconnect
     * (plan Sections 5.1 and 5.3, point 5).
     *
     * @param exerciseId the source exercise id
     * @return 200 with the running job, or 204 when none
     */
    @GetMapping("exercises/{exerciseId}/generate-variant/active")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<VariantJobDTO> getActiveJob(@PathVariable long exerciseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        return jobService.getActiveJob(user, exerciseId).map(job -> ResponseEntity.ok(VariantJobDTO.of(job))).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * GET variant-jobs : the current user's jobs (running + retained-finished) for the navbar tray
     * (plan Sections 5.1 and 5.4). Per-user scoping is enforced in the service.
     *
     * @return 200 with the job list
     */
    @GetMapping("variant-jobs")
    @EnforceAtLeastEditor
    public ResponseEntity<List<VariantJobDTO>> getJobsOfCurrentUser() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(jobService.getJobsOfUser(user.getLogin()).stream().map(VariantJobDTO::of).toList());
    }

    /**
     * GET variant-jobs/{jobId} : full job detail incl. per-phase step outputs for reopening the modal in
     * monitor mode (plan Sections 5.1 and 5.4). Unknown, expired, and foreign jobs are all 404 so job ids
     * cannot be probed.
     *
     * @param jobId the job id
     * @return 200 with the detail, or 404
     */
    @GetMapping("variant-jobs/{jobId}")
    @EnforceAtLeastEditor
    public ResponseEntity<VariantJobDetailDTO> getJobDetail(@PathVariable String jobId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        return jobService.getJob(jobId, user.getLogin()).map(job -> ResponseEntity.ok(VariantJobDetailDTO.of(job))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * DELETE variant-jobs/{jobId} : cooperative cancel (plan Section 5.2) — sets the distributed flag; the
     * pipeline observes it at the next phase/agent-round boundary and cleans up the provisioned clone.
     *
     * @param jobId the job id
     * @return 204; 409 when the job already reached FINALIZING or a terminal phase
     */
    @DeleteMapping("variant-jobs/{jobId}")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> cancelJob(@PathVariable String jobId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        jobService.requestCancel(jobId, user.getLogin());
        log.info("Cancellation requested for variant generation job [{}]", jobId);
        return ResponseEntity.noContent().build();
    }

    private void validateRequest(Exercise exercise, VariantGenerationRequestDTO request) {
        if (!request.hasAnyIntent()) {
            throw new BadRequestAlertException("At least one variant intent must be provided", ENTITY_NAME, "noIntentSelected");
        }
        if (!typeRegistry.isSupported(exercise.getExerciseType())) {
            throw new BadRequestAlertException("Variant generation is not supported for exercise type " + exercise.getExerciseType(), ENTITY_NAME, "unsupportedType");
        }
        validatePlacement(exercise, request.placement());
    }

    private void validatePlacement(Exercise exercise, VariantPlacementDTO placement) {
        if (placement == null || placement.type() == null) {
            throw new BadRequestAlertException("A placement choice is required", ENTITY_NAME, "missingPlacement");
        }
        if (exercise.isExamExercise()) {
            // Exam exercises have exactly one placement: the source's exam exercise group (plan Section 5.5).
            if (placement.type() != VariantPlacementDTO.PlacementType.SAME_EXAM_GROUP) {
                throw new BadRequestAlertException("Exam variants are always placed in the source's exam exercise group", ENTITY_NAME, "invalidExamPlacement");
            }
            return;
        }
        switch (placement.type()) {
            case SAME_EXAM_GROUP -> throw new BadRequestAlertException("SAME_EXAM_GROUP is only valid for exam exercises", ENTITY_NAME, "invalidPlacement");
            case EXISTING_GROUP -> {
                if (placement.existingGroupId() == null) {
                    throw new BadRequestAlertException("existingGroupId is required for EXISTING_GROUP placement", ENTITY_NAME, "missingGroupId");
                }
            }
            case NEW_GROUP -> {
                if (placement.newGroup() == null || placement.newGroup().title() == null || placement.newGroup().title().isBlank()) {
                    throw new BadRequestAlertException("A group title is required for NEW_GROUP placement", ENTITY_NAME, "missingGroupTitle");
                }
            }
            case STANDALONE -> {
                // nothing to validate
            }
        }
    }
}
