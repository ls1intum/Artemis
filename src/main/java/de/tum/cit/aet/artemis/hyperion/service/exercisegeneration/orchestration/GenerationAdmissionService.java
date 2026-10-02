package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.config.HyperionGenerationCapacityHealthIndicator;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationInputDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.service.HyperionReviewCommentContextRendererService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.GenerationCapabilityService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.GenerationRequestService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.LanguageGenerationProfile;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/** The common policy, feedback capture and budget boundary for programming authoring. */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationAdmissionService {

    private static final Logger log = LoggerFactory.getLogger(GenerationAdmissionService.class);

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    private final ProgrammingExerciseRepository exerciseRepository;

    private final GenerationCapabilityService capabilities;

    private final GenerationJobService jobService;

    private final GenerationRequestService generationRequestService;

    private final HyperionReviewCommentContextRendererService reviewCommentContextRenderer;

    private final GenerationWorkerRegistryService workerRegistry;

    private final HyperionGenerationBudgetService generationBudgetService;

    private final HyperionGenerationCapacityHealthIndicator generationCapacityHealthIndicator;

    private final HyperionEffortProfileService effortProfileService;

    public GenerationAdmissionService(ProgrammingExerciseRepository exerciseRepository, GenerationCapabilityService capabilities, GenerationJobService jobService,
            GenerationRequestService generationRequestService, HyperionReviewCommentContextRendererService reviewCommentContextRenderer,
            GenerationWorkerRegistryService workerRegistry, HyperionGenerationBudgetService generationBudgetService,
            HyperionGenerationCapacityHealthIndicator generationCapacityHealthIndicator, HyperionEffortProfileService effortProfileService) {
        this.exerciseRepository = exerciseRepository;
        this.capabilities = capabilities;
        this.jobService = jobService;
        this.generationRequestService = generationRequestService;
        this.reviewCommentContextRenderer = reviewCommentContextRenderer;
        this.workerRegistry = workerRegistry;
        this.generationBudgetService = generationBudgetService;
        this.generationCapacityHealthIndicator = generationCapacityHealthIndicator;
        this.effortProfileService = effortProfileService;
    }

    /**
     * Admits one run after the caller has authorized access to its destination.
     *
     * @param user       requesting editor
     * @param exerciseId authorized destination exercise
     * @param request    requested transformation and bounded effort
     * @return the common job id
     */
    public String start(User user, long exerciseId, ExerciseGenerationRequestDTO request) {
        ReservedRun run = reserve(user, exerciseId, request, false);
        try {
            String jobId = jobService.startJob(user, run.source(), run.prompt(), request.mode(), run.budgetReservationId(), run.sourceBrief(), run.settings(), run.input());
            log.info("Started authoring job {} ({}) for exercise {}", jobId, request.mode(), exerciseId);
            return jobId;
        }
        catch (RuntimeException failure) {
            release(run);
            throw failure;
        }
    }

    /**
     * Reserves the same bounded authoring effort for adaptation into a new destination.
     *
     * @param user     authorized editor
     * @param sourceId authorized source exercise
     * @param request  structured transformation
     * @return reservation to release if destination creation or dispatch fails
     */
    public ReservedRun reserveVariant(User user, long sourceId, VariantGenerationRequestDTO request) {
        if (!request.hasAnyIntent()) {
            throw new BadRequestAlertException("At least one variant intent must be provided", ENTITY_NAME, "noIntentSelected");
        }
        StringBuilder instructions = new StringBuilder(
                "Create a distinct variant of the source exercise. Preserve its learning objectives and all behavior not explicitly changed below.\n");
        if (request.targetDifficulty() != null) {
            instructions.append("Requested difficulty: ").append(request.targetDifficulty()).append('\n');
        }
        if (request.domainText() != null && !request.domainText().isBlank()) {
            instructions.append("Requested domain: ").append(request.domainText()).append('\n');
        }
        if (request.narrativeStyle() != null) {
            instructions.append("Requested narrative style: ").append(request.narrativeStyle()).append('\n');
        }
        if (request.additionalInstructions() != null && !request.additionalInstructions().isBlank()) {
            instructions.append("Additional instructions: ").append(request.additionalInstructions()).append('\n');
        }
        return reserve(user, sourceId, new ExerciseGenerationRequestDTO(GenerationMode.ADAPT, instructions.toString(), null), true);
    }

    /**
     * Releases a reservation whose asynchronous job did not take responsibility for it.
     *
     * @param run reserved request
     */
    public void release(ReservedRun run) {
        generationBudgetService.releaseReservation(run.budgetReservationId());
    }

    private ReservedRun reserve(User user, long exerciseId, ExerciseGenerationRequestDTO request, boolean newDestination) {
        validateSelectedFeedbackThreadIds(request.selectedFeedbackThreadIds());
        validateRequestedJobDuration(request.maxJobDuration());
        // Fail-closed on an unknown profile name: falling back to the default profile would silently spend a budget nobody asked for.
        HyperionGenerationSettings settings = effortProfileService.resolve(request.effortProfile()).tightenedBy(request.maxTokens(), request.maxJobDuration());
        ProgrammingExercise exercise = exerciseRepository.findWithAllParticipationsById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise", exerciseId));
        if (!newDestination || exercise.isExamExercise()) {
            capabilities.requireMutable(exercise);
        }
        capabilities.requireSupportedConfiguration(exercise);
        if (!newDestination) {
            jobService.rejectIfActiveJobCannotBeReclaimed(exerciseId);
        }
        if (request.mode() == GenerationMode.GENERATE && (request.prompt() == null || request.prompt().isBlank())
                && !generationRequestService.isAuthoritativeProblemStatement(exercise)) {
            throw new BadRequestAlertException("Enter a brief before generating an exercise without a problem statement.", ENTITY_NAME, "generationBriefRequired");
        }
        if (!workerRegistry.hasAvailableGenerationSandboxSlot(LanguageGenerationProfile.toolchainFor(exercise))) {
            generationCapacityHealthIndicator.warnGenerationRejectedForMissingCapacity();
            throw new ServiceUnavailableAlertException("No compatible Hyperion worker currently has a free generation slot.", ENTITY_NAME, "generationCapacityUnavailable");
        }
        Long courseId = courseIdOf(exercise);
        var feedback = selectedFeedback(exerciseId, request);
        String prompt = generationRequestService.resolvePrompt(request, exercise);
        if (!feedback.prompt().isBlank()) {
            prompt += "\n\n" + feedback.prompt();
        }
        // Reserves what this run may spend rather than the fleet-wide worst case, so a course drafting small exercises is not throttled at the largest job's cost.
        HyperionGenerationBudgetService.BudgetReservation budgetReservation = generationBudgetService.reserveGenerationBudget(user.getId(), courseId, settings.maxTokensPerJob());
        String sourceBrief = request.mode() == GenerationMode.GENERATE && request.prompt() != null && !request.prompt().isBlank() ? request.prompt().strip() : null;
        return new ReservedRun(user, exercise, request, settings, prompt, sourceBrief,
                new ExerciseGenerationInputDTO(request.prompt(), feedback.feedback(), newDestination ? exerciseId : null), budgetReservation.id());
    }

    /** Immutable admission result: one resolved prompt, one effort profile and one budget reservation. */
    public record ReservedRun(User user, ProgrammingExercise source, ExerciseGenerationRequestDTO request, HyperionGenerationSettings settings, String prompt,
            @Nullable String sourceBrief, ExerciseGenerationInputDTO input, String budgetReservationId) {
    }

    private HyperionReviewCommentContextRendererService.SelectedFeedback selectedFeedback(long exerciseId, ExerciseGenerationRequestDTO request) {
        if (request.mode() != GenerationMode.ADAPT || request.selectedFeedbackThreadIds() == null || request.selectedFeedbackThreadIds().isEmpty()) {
            return new HyperionReviewCommentContextRendererService.SelectedFeedback("", List.of());
        }
        return reviewCommentContextRenderer.captureWholeExerciseSelectedFeedback(exerciseId, request.selectedFeedbackThreadIds());
    }

    private static Long courseIdOf(ProgrammingExercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return course == null ? null : course.getId();
    }

    /**
     * Bean validation has no positivity constraint for {@link Duration}, and a non-positive bound would clamp the run to a deadline it can never meet rather than tighten it.
     */
    private void validateRequestedJobDuration(@Nullable Duration maxJobDuration) {
        if (maxJobDuration != null && (maxJobDuration.isZero() || maxJobDuration.isNegative())) {
            throw new BadRequestAlertException("The requested maximum job duration must be positive.", ENTITY_NAME, "invalidMaxJobDuration");
        }
    }

    /** Complements the {@code @Size} cap on the DTO, which bounds how many ids may be sent but not what they may be. */
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
