package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.Phase;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRepairRoundDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderUsageSink;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderUsageTransport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationFileUpdate;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationSeedService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerClientService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Coordinates a worker execution. Core retains ownership, accounting, instructor progress and persistence authority. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationOrchestrationService.class);

    private final GenerationSeedService seeds;

    private final GenerationWorkerRegistryService workers;

    private final GenerationWorkerClientService client;

    private final GenerationJobService jobs;

    private final HyperionAgentProperties properties;

    private final HyperionEffortProfileService profiles;

    public GenerationOrchestrationService(GenerationSeedService seeds, GenerationWorkerRegistryService workers, GenerationWorkerClientService client, GenerationJobService jobs,
            HyperionAgentProperties properties, HyperionEffortProfileService profiles) {
        this.seeds = seeds;
        this.workers = workers;
        this.client = client;
        this.jobs = jobs;
        this.properties = properties;
        this.profiles = profiles;
    }

    /**
     * Captures immutable input, runs a job on one worker and returns captured evidence without executing model code on core.
     *
     * @param exercise      authorized draft exercise
     * @param user          initiating instructor
     * @param prompt        resolved generation/adaptation instruction
     * @param jobId         core job identifier
     * @param mode          generation or adaptation
     * @param stopAuthoring spend/deadline/ownership stop signal; not necessarily instructor cancellation
     * @param progress      existing public progress projection
     * @param files         existing file preview projection
     * @param usage         existing core per-call accounting consumer
     * @param sourceBrief   unmodified source requirements
     * @param deadline      absolute admission-time deadline
     * @param settings      resolved effort profile
     * @return captured output for core's guarded persistence decision
     */
    public GenerationOutcome generate(ProgrammingExercise exercise, User user, String prompt, String jobId, GenerationMode mode, BooleanSupplier stopAuthoring,
            GenerationProgressSink progress, Consumer<GenerationFileUpdate> files, Consumer<ChatResponse> usage, @Nullable String sourceBrief, Instant deadline,
            @Nullable HyperionGenerationSettings settings) {
        HyperionGenerationSettings resolved = settings == null ? profiles.resolve(null) : settings;
        var seed = seeds.capture(exercise);
        var latest = new AtomicReference<GenerationOutput>();
        var terminal = new AtomicReference<WorkerEvent>();
        Set<Long> processed = new HashSet<>();
        String statement = seed.snapshot().files().stream().filter(file -> file.path().equals("problem-statement.md"))
                .map(file -> new String(file.content(), StandardCharsets.UTF_8)).findFirst().orElse("");
        var brief = new ExerciseBrief(exercise.getTitle(), exercise.getShortName(), exercise.getPackageName(), statement, prompt, ExerciseBrief.Mode.valueOf(mode.name()),
                sourceBrief);
        var parameters = parameters(resolved);
        var claim = workers.claim(jobId, exercise.getId());
        try {
            var assignment = new GenerationAssignment(claim.identity(), brief, parameters, seed.snapshot(), deadline, claim.imageDigest());
            client.send(new WorkerCommand(1, WorkerCommand.Type.START, claim.identity(), assignment));
            Instant renewAt = Instant.MIN;
            boolean stopped = false;
            while (terminal.get() == null) {
                if (!jobs.isOwnedActiveJob(exercise.getId(), jobId)) {
                    markUncertain(usage);
                    return GenerationOutcome.stopped(seed, true, "Generation ownership was lost.");
                }
                if (!stopped && stopAuthoring.getAsBoolean()) {
                    WorkerCommand.Type stop = jobs.isCancelled(jobId) ? WorkerCommand.Type.CANCEL : WorkerCommand.Type.STOP_AUTHORING;
                    client.send(new WorkerCommand(1, stop, claim.identity(), null));
                    stopped = true;
                }
                if (!Instant.now().isBefore(renewAt)) {
                    if (!workers.renew(claim)) {
                        throw new IllegalStateException("Generation worker ownership or presence was lost");
                    }
                    client.send(new WorkerCommand(1, WorkerCommand.Type.RENEW, claim.identity(), null));
                    renewAt = Instant.now().plusSeconds(10);
                }
                if (Instant.now().isAfter(deadline.plus(Duration.ofMinutes(5)))) {
                    throw new IllegalStateException("Worker did not finish within the authoring drain window");
                }
                client.receive(claim, event -> {
                    if (processed.contains(event.sequence())) {
                        return;
                    }
                    if (processed.size() >= 100_000) {
                        throw new IllegalStateException("Generation event budget exceeded");
                    }
                    if (event.output() != null && !resolved.name().equals(event.output().effortProfile())) {
                        throw new IllegalArgumentException("Worker output does not attest the assigned effort profile");
                    }
                    apply(event, progress, files, usage);
                    if (event.type() == WorkerEvent.Type.CHECKPOINT) {
                        GenerationOutcome captured = GenerationOutcome.received(event.output(), seed, false);
                        jobs.retainUnsavedArtifacts(exercise.getId(), jobId, user.getLogin(),
                                RetainedArtifacts.of(jobId, captured.capturedProducedFiles(), captured.producedProblemStatement(), captured.specDocument()));
                        latest.set(event.output());
                    }
                    if (event.type() == WorkerEvent.Type.FINISHED || event.type() == WorkerEvent.Type.CANCELLED || event.type() == WorkerEvent.Type.ERROR) {
                        workers.recordCompletion(claim, event);
                        terminal.set(event);
                    }
                    processed.add(event.sequence());
                });
            }
            WorkerEvent finished = terminal.get();
            GenerationOutput output = finished.output();
            if (output == null || output.accountingState() != GenerationOutput.AccountingState.COMPLETE || !matchesAccount(output, user, exercise)) {
                markUncertain(usage);
            }
            if (output == null) {
                output = reviewedFallback(latest.get());
            }
            boolean cancelled = jobs.isCancelled(jobId) || finished.type() == WorkerEvent.Type.CANCELLED;
            return output == null ? GenerationOutcome.stopped(seed, cancelled, "Generation stopped without a candidate.") : GenerationOutcome.received(output, seed, cancelled);
        }
        catch (RuntimeException failure) {
            log.warn("Generation worker execution {} stopped ({})", claim.identity().executionId(), failure.getClass().getSimpleName());
            markUncertain(usage);
            GenerationOutput fallback = reviewedFallback(latest.get());
            return fallback == null ? GenerationOutcome.stopped(seed, jobs.isCancelled(jobId), "Generation worker became unavailable.")
                    : GenerationOutcome.received(fallback, seed, jobs.isCancelled(jobId));
        }
        finally {
            try {
                client.send(new WorkerCommand(1, WorkerCommand.Type.CANCEL, claim.identity(), null));
            }
            catch (RuntimeException ignored) {
                log.warn("Could not deliver worker cleanup request; the worker stops when core renewals expire");
            }
            workers.release(claim);
        }
    }

    private GenerationParameters parameters(HyperionGenerationSettings settings) {
        var options = settings.chatOptions();
        return new GenerationParameters(settings.name(), settings.maxTurns(), settings.maxTokensPerJob(), settings.maxJobDuration(), settings.contextWindowTokens(),
                options == null ? null : options.getModel(), options == null ? null : options.getReasoningEffort(), options == null ? null : options.getTemperature(),
                options == null ? null : options.getTopP(), options == null ? null : options.getMaxCompletionTokens(), settings.stagedGeneration(), settings.stagedContext(),
                options == null ? null : options.getVerbosity(), properties.getCachedInputTokenWeight());
    }

    private boolean matchesAccount(GenerationOutput output, User user, ProgrammingExercise exercise) {
        var reported = output.usage();
        var recorded = jobs.getStatus(user, exercise).map(status -> status.usage()).orElse(null);
        return reported != null && recorded != null && reported.modelCalls() == recorded.modelCalls() && reported.toolCalls() == recorded.toolCalls()
                && reported.agentTurns() == recorded.agentTurns() && reported.attempts() == recorded.attempts() && reported.inputTokens() == recorded.inputTokens()
                && reported.outputTokens() == recorded.outputTokens() && reported.cachedInputTokens() == recorded.cachedInputTokens()
                && reported.providerRequestIds().equals(recorded.providerRequestIds()) && reported.models().equals(recorded.models());
    }

    private static void apply(WorkerEvent event, GenerationProgressSink progress, Consumer<GenerationFileUpdate> files, Consumer<ChatResponse> usage) {
        var detail = event.progress();
        if (detail == null) {
            if (event.activity() != null) {
                progress.activity(event.message(), event.activity());
            }
            else if (event.message() != null) {
                progress.accept(event.message());
            }
            return;
        }
        if (detail.phase() != null) {
            progress.phase(Phase.valueOf(detail.phase()), event.message());
        }
        else if (detail.repairRound() != null) {
            var round = detail.repairRound();
            progress.progress(event.message(),
                    new ExerciseGenerationRepairRoundDTO(round.round(), round.attempt(), round.blocking(), round.advisory(), round.carriedOver(), round.drained(), round.fresh()));
        }
        else if (detail.fileChange() != null) {
            var file = detail.fileChange();
            String repo = file.path().startsWith("solution/") ? "solution"
                    : file.path().startsWith("template/") ? "template" : file.path().startsWith("tests/") ? "tests" : "other";
            files.accept(
                    new GenerationFileUpdate(new ExerciseGenerationFileChangeDTO("FILE_CHANGE", file.path(), repo, file.action(), file.turn(), file.timestamp()), file.content()));
        }
        else if (detail.usage() != null && usage instanceof ProviderUsageSink sink) {
            var update = detail.usage();
            switch (update.kind()) {
                case RESPONSE -> sink.accept(ProviderUsageTransport.restore(update.call()));
                case TOOL_CALLS -> sink.recordToolCalls(update.count());
                case TURN -> sink.recordTurn();
                case ATTEMPT -> sink.recordAttempt();
                case UNCERTAIN -> sink.markUncertain();
            }
        }
    }

    private static void markUncertain(Consumer<ChatResponse> usage) {
        if (usage instanceof ProviderUsageSink sink) {
            sink.markUncertain();
        }
    }

    @Nullable
    private static GenerationOutput reviewedFallback(@Nullable GenerationOutput checkpoint) {
        if (checkpoint == null || !checkpoint.verification().mechanicallyVerified()) {
            return null;
        }
        var findings = new ArrayList<>(checkpoint.review().findings());
        findings.addAll(SpecFidelityReport.qualityReviewUnavailable("The worker stopped before delivering its final result.").findings());
        return new GenerationOutput(checkpoint.candidate(), checkpoint.verification(), checkpoint.verifiedDigest(), new SpecFidelityReport(findings), "RUN_FAILED",
                checkpoint.usage(), GenerationOutput.AccountingState.INCOMPLETE, checkpoint.effortProfile());
    }
}
