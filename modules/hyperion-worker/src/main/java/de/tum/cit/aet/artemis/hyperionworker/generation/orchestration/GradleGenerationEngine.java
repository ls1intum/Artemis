package de.tum.cit.aet.artemis.hyperionworker.generation.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationActivity;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationProgress;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.GenerationActivityTracker;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HarmonyScrubbingChatModel;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationInput;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationResources;
import de.tum.cit.aet.artemis.hyperionworker.generation.PromptTemplates;
import de.tum.cit.aet.artemis.hyperionworker.generation.WorkerUsageRecorder;
import de.tum.cit.aet.artemis.hyperionworker.generation.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperionworker.generation.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperionworker.generation.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperionworker.generation.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperionworker.generation.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperionworker.generation.verification.StageCheckService;
import de.tum.cit.aet.artemis.hyperionworker.generation.verification.StructuralOracleSeedingService;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.SandboxBuildCommandService;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.DockerSandbox;
import de.tum.cit.aet.artemis.hyperionworker.session.GenerationEngine;
import de.tum.cit.aet.artemis.hyperionworker.session.GenerationObserver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/** Worker-local generation policy. Each assignment gets fresh verification, specification and repair state. */
@Service
public class GradleGenerationEngine implements GenerationEngine {

    private final ObservationRegistry observations;

    private final DockerSandbox sandbox;

    private final List<ChatModel> models;

    private final int maxSemanticRepairs;

    private final boolean retriesDisabled;

    private final Duration providerTimeout;

    private final AgentTranscriptWriter transcripts;

    private final Map<ExecutionIdentity, GenerationOrchestrationService> active = new ConcurrentHashMap<>();

    private final ProviderFailureCooldown cooldown = new ProviderFailureCooldown() {

        private final Map<String, Instant> deadlines = new ConcurrentHashMap<>();

        @Override
        public @Nullable Instant cooldownUntil(String key) {
            Instant deadline = deadlines.get(key);
            return deadline != null && deadline.isAfter(Instant.now()) ? deadline : null;
        }

        @Override
        public void startCooldown(String key, Instant until) {
            deadlines.entrySet().removeIf(entry -> !entry.getValue().isAfter(Instant.now()));
            deadlines.put(key, until);
        }
    };

    public GradleGenerationEngine(DockerSandbox sandbox, Collection<ChatModel> models, ObservationRegistry observations,
            @Value("${artemis.hyperion.agent.max-semantic-repairs:6}") int maxSemanticRepairs, @Value("${spring.ai.openai.max-retries:1}") int providerRetries,
            @Value("${spring.ai.openai.chat.timeout:${spring.ai.openai.timeout:60s}}") Duration providerTimeout,
            @Value("${artemis.hyperion.agent.transcript-dir:}") String transcriptDirectory) {
        if (models.size() != 1 || maxSemanticRepairs < 1 || maxSemanticRepairs > 12 || providerRetries < 0) {
            throw new IllegalArgumentException("Configure one worker chat model, 1–12 semantic repairs and a nonnegative provider retry limit");
        }
        if (providerTimeout.isZero() || providerTimeout.isNegative()) {
            throw new IllegalArgumentException("The worker provider timeout must be positive");
        }
        this.observations = observations;
        this.sandbox = sandbox;
        this.models = List.copyOf(models);
        this.maxSemanticRepairs = maxSemanticRepairs;
        this.retriesDisabled = providerRetries == 0;
        this.providerTimeout = providerTimeout;
        this.transcripts = new AgentTranscriptWriter(transcriptDirectory);
    }

    @Override
    public GenerationOutput generate(GenerationAssignment assignment, BooleanSupplier cancelled, GenerationObserver observer, Consumer<GenerationOutput> checkpoint) {
        var identity = assignment.identity();
        return Observation.createNotStarted("hyperion.generation", observations).highCardinalityKeyValue("artemis.hyperion.job.id", identity.jobId())
                .highCardinalityKeyValue("artemis.hyperion.execution.id", identity.executionId().toString())
                .highCardinalityKeyValue("artemis.exercise.id", Long.toString(identity.exerciseId()))
                .lowCardinalityKeyValue("artemis.hyperion.effort_profile", assignment.parameters().effortProfile())
                .observe(() -> generateObserved(assignment, cancelled, observer, checkpoint));
    }

    private GenerationOutput generateObserved(GenerationAssignment assignment, BooleanSupplier cancelled, GenerationObserver observer, Consumer<GenerationOutput> checkpoint) {
        var parameters = assignment.parameters();
        var usage = new WorkerUsageRecorder(parameters.maxTokens(), parameters.cachedInputTokenWeight(), retriesDisabled,
                event -> observer.progress("Provider usage recorded.", new GenerationProgress(null, null, null, event)));
        BooleanSupplier stopAuthoring = () -> cancelled.getAsBoolean() || usage.budgetExhausted() || !Instant.now().isBefore(assignment.authoringDeadline());
        var progress = progressSink(observer);
        var resources = new GenerationResources();
        var commands = new SandboxBuildCommandService(resources);
        var workspace = new GenerationWorkspaceService(commands, resources);
        var approvedSpecs = new ApprovedSpecRegistry();
        var verifier = new DifferentialVerificationService(commands, approvedSpecs);
        var stageChecks = new StageCheckService(verifier, approvedSpecs);
        var prompt = new AgentSystemPromptService(commands);
        var runner = new AgentLoopRunner(models, parameters.contextWindowTokens(), Duration.ofMinutes(5), cooldown);
        var model = models.getFirst();
        var critic = new SpecFidelityCriticService(ChatClient.create(new HarmonyScrubbingChatModel(model)), new ObjectMapper(), new PromptTemplates(),
                model.getOptions().getModel(), Duration.ofMinutes(5), cooldown, parameters.contextWindowTokens(), models);
        var stages = new StagedGenerationRunner(runner, prompt, stageChecks, transcripts, approvedSpecs, critic, new ExerciseConceptSelector(runner, critic),
                parameters.stagedContext(), parameters.maxDuration());
        var orchestrator = new GenerationOrchestrationService(sandbox.forExecution(assignment.identity().executionId()), workspace, runner, verifier, prompt,
                new StructuralOracleSeedingService(workspace, approvedSpecs), critic, parameters.maxTurns(), maxSemanticRepairs, stages, parameters.stagedGeneration(), stageChecks,
                transcripts, approvedSpecs);
        var brief = assignment.brief();
        var input = new GenerationInput(assignment.identity().exerciseId(), brief.packageName(), brief.problemStatement(), assignment.gradingContext().hasDueDate(),
                assignment.gradingContext().baselineGradedTestNames());
        Consumer<GenerationProgress.FileChange> files = change -> observer.progress("Updated " + change.path(), new GenerationProgress(null, null, change, null));
        active.put(assignment.identity(), orchestrator);
        try {
            var outcome = orchestrator.generate(input, assignment.seed(), brief.prompt(), assignment.identity().jobId(), brief.mode(), stopAuthoring, progress, files, usage,
                    brief.sourceBrief(), settings(parameters), spec -> files.accept(new GenerationProgress.FileChange("SPEC.md", "write", 0, Instant.now(), boundedSpec(spec))),
                    candidate -> checkpoint.accept(GenerationOutputCapture.capture(assignment, candidate, usage, true)));
            return GenerationOutputCapture.capture(assignment, outcome, usage, false);
        }
        finally {
            active.remove(assignment.identity(), orchestrator);
        }
    }

    @Override
    public boolean requestCancel(ExecutionIdentity identity) {
        GenerationOrchestrationService orchestrator = active.get(identity);
        if (orchestrator == null) {
            return false;
        }
        orchestrator.requestCancel();
        return true;
    }

    HyperionGenerationSettings settings(GenerationParameters parameters) {
        var defaults = models.getFirst().getOptions();
        var builder = defaults instanceof OpenAiChatOptions openAi ? openAi.mutate() : OpenAiChatOptions.builder();
        // Spring AI's default chat options carry 60s even when its configured HTTP client has a longer timeout.
        builder.timeout(providerTimeout);
        if (parameters.model() != null) {
            builder.model(parameters.model());
        }
        if (parameters.reasoningEffort() != null) {
            builder.reasoningEffort(parameters.reasoningEffort());
        }
        if (parameters.temperature() != null) {
            builder.temperature(parameters.temperature());
        }
        if (parameters.topP() != null) {
            builder.topP(parameters.topP());
        }
        if (parameters.maxCompletionTokens() != null) {
            builder.maxTokens(null).maxCompletionTokens(parameters.maxCompletionTokens());
        }
        if (parameters.verbosity() != null) {
            builder.verbosity(parameters.verbosity());
        }
        return new HyperionGenerationSettings(parameters.effortProfile(), null, parameters.maxTurns(), parameters.maxDuration(), parameters.maxTokens(),
                parameters.stagedGeneration(), parameters.stagedContext(), parameters.contextWindowTokens(), builder.build(), false, true);
    }

    private static String boundedSpec(String spec) {
        return spec.length() <= 65_536 ? spec : spec.substring(0, 65_536);
    }

    private static GenerationProgressSink progressSink(GenerationObserver observer) {
        return new GenerationProgressSink() {

            @Override
            public void accept(String message) {
                observer.accept(message);
            }

            @Override
            public GenerationActivityTracker activityTracker() {
                return observer.activityTracker();
            }

            @Override
            public void activity(String message, GenerationActivity activity) {
                observer.activity(message, activity);
            }

            @Override
            public void phase(Phase phase, String message) {
                observer.progress(message, new GenerationProgress(phase.name(), null, null, null));
            }

            @Override
            public void progress(String message, GenerationProgress.RepairRound round) {
                observer.progress(message, new GenerationProgress(null, round, null, null));
            }
        };
    }
}
