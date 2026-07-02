package de.tum.cit.aet.artemis.hyperion.exercisegeneration.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Runs an agentic whole-exercise generation/adaptation session asynchronously and streams progress to the instructor over the existing Hyperion websocket topic.
 * <p>
 * It owns the end-to-end flow: drive the {@link ExerciseGenerationOrchestrationService} and stream every terminal state as a clear, distinct event — {@code SUCCESS} (verified),
 * {@code PARTIAL} (verification did not pass / nothing usable was produced), plus cancellation and error. The verdict {@link ExerciseGenerationVerdictDTO} is mirrored to the
 * client
 * so it can render which gates passed without parsing prose.
 * <p>
 * Persisting a verified exercise through Artemis's normal create/update pipeline (and recovering a near-miss as a reviewable draft) is layered on by the persistence commit: this
 * engine-core commit stops at streaming the differential verdict. The {@link GenerationOutcome} exposes the produced files and problem statement for that hand-off, and is always
 * closed here so the sandbox container is destroyed even when persistence is not yet wired.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ExerciseGenerationTaskService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseGenerationTaskService.class);

    private static final String TOPIC_PREFIX = "exercise-generation/jobs/";

    private final ExerciseGenerationOrchestrationService orchestrator;

    private final HyperionWebsocketService websocket;

    private final ExerciseGenerationJobService jobService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public ExerciseGenerationTaskService(ExerciseGenerationOrchestrationService orchestrator, HyperionWebsocketService websocket, ExerciseGenerationJobService jobService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.orchestrator = orchestrator;
        this.websocket = websocket;
        this.jobService = jobService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Runs one generation/adaptation session, triggered by the {@link ExerciseGenerationStartedEvent} the job service publishes. Runs on the dedicated generation executor (via
     * {@link Async}) so it returns the request thread immediately.
     *
     * @param event the start event carrying the job id, requesting user, target exercise, and prompt
     */
    @Async("hyperionGenerationExecutor")
    @EventListener
    public void runAsync(ExerciseGenerationStartedEvent event) {
        String jobId = event.jobId();
        User user = event.user();
        String userPrompt = event.userPrompt();
        long exerciseId = event.exercise().getId();
        String login = user.getLogin();
        String topic = TOPIC_PREFIX + jobId;
        GenerationProgressEmitter emitter = new GenerationProgressEmitter((progressEvent, terminal) -> jobService.recordEvent(exerciseId, jobId, progressEvent, terminal),
                progressEvent -> websocket.send(login, topic, progressEvent));
        // The event carries an exercise loaded on the request thread; on this async executor thread its lazy associations (buildConfig, template/solution participations) are
        // detached, so touching them (e.g. buildConfig.getBranch() during seeding) would throw LazyInitializationException. Re-load it with exactly those associations eagerly
        // initialized — and fail CLOSED with a clear terminal error if it has since been deleted, rather than falling back to the detached entity and re-triggering that exception.
        ProgrammingExercise exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndBuildConfigById(exerciseId).orElse(null);
        if (exercise == null) {
            log.error("Exercise generation job {} aborted: programming exercise {} no longer exists", jobId, exerciseId);
            emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Generation failed: the exercise no longer exists."));
            jobService.clearJob(exerciseId, jobId);
            return;
        }
        emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "Starting exercise generation"));
        try (GenerationOutcome outcome = orchestrator.generate(exercise, user, userPrompt, jobId, () -> jobService.isCancelled(jobId), emitter::progress)) {
            switch (outcome.loopResult().status()) {
                case CANCELLED -> emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, "Generation was cancelled. Nothing was changed."));
                case ERROR -> emitter.milestone(
                        ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, outcome.errorMessage() != null ? outcome.errorMessage() : "Generation failed."));
                default -> {
                    ExerciseGenerationVerdictDTO verdict = toVerdict(outcome.verification());
                    // The persistence commit replaces these two branches with the real persist / recovery hand-off; engine-core streams the differential verdict as the terminal
                    // state so the flow is observable end-to-end without a second grading path.
                    if (outcome.isAccepted()) {
                        emitter.milestone(ExerciseGenerationEventDTO.done("The exercise passed verification.", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, verdict));
                    }
                    else {
                        String reason = outcome.verification() != null ? outcome.verification().report() : "The exercise could not be completed within the budget.";
                        emitter.milestone(ExerciseGenerationEventDTO.done(reason, ExerciseGenerationEventDTO.CompletionStatus.PARTIAL, verdict));
                    }
                }
            }
        }
        catch (RuntimeException e) {
            log.error("Exercise generation job {} failed", jobId, e);
            emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Generation failed: " + e.getMessage()));
        }
        finally {
            jobService.clearJob(exerciseId, jobId);
        }
    }

    private static ExerciseGenerationVerdictDTO toVerdict(VerificationResult verification) {
        if (verification == null) {
            return null;
        }
        return new ExerciseGenerationVerdictDTO(verification.accepted(), verification.solutionPassed(), verification.templateFailed(), verification.testCount(),
                verification.reasons());
    }
}
