package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.lang.model.SourceVersion;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.api.SandboxApi;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationActivity;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationProgress;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationToolchain;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.GenerationActivityTracker;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HarmonyScrubbingChatModel;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.hyperion.service.worker.GenerationObserver;
import de.tum.cit.aet.artemis.hyperion.service.worker.ToolchainGenerationAdapter;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationInput;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationResources;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.PromptTemplates;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.WorkerUsageRecorder;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.AgentSystemPrompt;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.DifferentialVerifier;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.StageChecks;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.StructuralOracleSeeder;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.GenerationWorkspace;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.SandboxBuildCommands;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/** Worker-local generation policy. Each assignment gets fresh verification, specification and repair state. */
@Service
@Lazy
@Profile(PROFILE_AIWORKER)
@ConditionalOnProperty(name = "artemis.aiworker.workload", havingValue = "hyperion-generation")
public class JavaGradleGenerationAdapterService implements ToolchainGenerationAdapter {

    private final ObservationRegistry observations;

    private final JsonMapper mapper;

    private final SandboxApi sandbox;

    private final LlmClientService llmClientService;

    private final int maxSemanticRepairs;

    private final AgentTranscriptWriter transcripts;

    private final Map<ExecutionIdentity, GenerationOrchestrator> active = new ConcurrentHashMap<>();

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

    public JavaGradleGenerationAdapterService(SandboxApi sandbox, LlmClientService llmClientService, JsonMapper mapper, ObservationRegistry observations,
            @Value("${artemis.hyperion.agent.max-semantic-repairs:6}") int maxSemanticRepairs, @Value("${artemis.hyperion.agent.transcript-dir:}") String transcriptDirectory) {
        if (maxSemanticRepairs < 1 || maxSemanticRepairs > 12) {
            throw new IllegalArgumentException("Configure 1–12 semantic repairs");
        }
        this.observations = observations;
        this.mapper = mapper;
        this.sandbox = sandbox;
        this.llmClientService = llmClientService;
        this.maxSemanticRepairs = maxSemanticRepairs;
        this.transcripts = new AgentTranscriptWriter(transcriptDirectory);
    }

    /**
     * Resolves the preset core assigned to this run in the worker's own configuration. Core and workers must carry the same {@code artemis.llm} block; a
     * worker without the preset, or with SDK retries for it, fails the run at its start instead of producing unaccounted calls.
     */
    ChatModel workerModel(String llmPreset) {
        try {
            llmClientService.preset(llmPreset);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "This AI worker has no LLM preset '" + llmPreset + "' under artemis.llm.presets; configure the same artemis.llm block on core and workers", e);
        }
        if (llmClientService.effectiveMaxRetries(llmPreset) != 0) {
            throw new IllegalStateException("The LLM preset '" + llmPreset + "' retries in the SDK; exercise generation needs max-retries 0 on the preset or its endpoint");
        }
        return llmClientService.chatModel(llmPreset);
    }

    @Override
    public GenerationToolchain toolchain() {
        return GenerationToolchain.JAVA_GRADLE;
    }

    @Override
    public GenerationOutput generate(GenerationAssignment assignment, BooleanSupplier cancelled, GenerationObserver observer, Consumer<GenerationOutput> checkpoint) {
        if (!toolchain().equals(assignment.brief().toolchain()) || !SourceVersion.isName(assignment.brief().namespace())) {
            throw new IllegalArgumentException("The Java Gradle adapter requires its own toolchain and a valid Java package name");
        }
        var identity = assignment.identity();
        return Observation.createNotStarted("hyperion.generation", observations).highCardinalityKeyValue("artemis.hyperion.job.id", identity.jobId())
                .highCardinalityKeyValue("artemis.hyperion.execution.id", identity.executionId().toString())
                .highCardinalityKeyValue("artemis.exercise.id", Long.toString(identity.exerciseId()))
                .lowCardinalityKeyValue("artemis.hyperion.effort_profile", assignment.parameters().effortProfile())
                .observe(() -> generateObserved(assignment, cancelled, observer, checkpoint));
    }

    private GenerationOutput generateObserved(GenerationAssignment assignment, BooleanSupplier cancelled, GenerationObserver observer, Consumer<GenerationOutput> checkpoint) {
        var parameters = assignment.parameters();
        ChatModel model = workerModel(parameters.llmPreset());
        // Presets of exercise generation never retry in the SDK (checked above), so every provider call is accounted for.
        var usage = new WorkerUsageRecorder(parameters.maxTokens(), parameters.cachedInputTokenWeight(),
                event -> observer.progress("Provider usage recorded.", new GenerationProgress(null, null, null, event)));
        BooleanSupplier stopAuthoring = stopAuthoring(cancelled, usage, assignment.authoringDeadline());
        var progress = progressSink(observer);
        var resources = new GenerationResources();
        var commands = new SandboxBuildCommands(resources);
        var workspace = new GenerationWorkspace(commands, resources);
        var approvedSpecs = new ApprovedSpecRegistry();
        var verifier = new DifferentialVerifier(commands, approvedSpecs);
        var stageChecks = new StageChecks(verifier, approvedSpecs);
        var prompt = new AgentSystemPrompt(commands);
        var runner = new AgentLoopRunner(List.of(model), parameters.contextWindowTokens(), Duration.ofMinutes(5), cooldown);
        var critic = new SpecFidelityCritic(ChatClient.create(new HarmonyScrubbingChatModel(model)), mapper, new PromptTemplates(), model.getOptions().getModel(),
                Duration.ofMinutes(5), cooldown, parameters.contextWindowTokens(), List.of(model));
        var stages = new StagedGenerationRunner(runner, prompt, stageChecks, transcripts, approvedSpecs, critic, new ExerciseConceptSelector(runner, critic),
                parameters.stagedContext(), parameters.maxDuration());
        var orchestrator = new GenerationOrchestrator(sandbox.forExecution(assignment.identity().executionId()), workspace, runner, verifier, prompt,
                new StructuralOracleSeeder(workspace, approvedSpecs), critic, parameters.maxTurns(), maxSemanticRepairs, stages, parameters.stagedGeneration(), stageChecks,
                transcripts, approvedSpecs);
        var brief = assignment.brief();
        var input = new GenerationInput(assignment.identity().exerciseId(), brief.namespace(), brief.problemStatement(), assignment.gradingContext().hasDueDate(),
                assignment.gradingContext().baselineGradedTestNames(), brief.mode() == ExerciseBrief.Mode.ADAPT ? assignment.gradingContext().baselineHiddenTestNames() : Set.of(),
                brief.resolvedBuildTool());
        Consumer<GenerationProgress.FileChange> files = change -> observer.progress("Updated " + change.path(), new GenerationProgress(null, null, change, null));
        active.put(assignment.identity(), orchestrator);
        try {
            var outcome = orchestrator.generate(input, assignment.seed(), brief.prompt(), assignment.identity().jobId(), brief.mode(), stopAuthoring, progress, files, usage,
                    brief.sourceBrief(), settings(parameters, model),
                    spec -> files.accept(new GenerationProgress.FileChange("SPEC.md", "write", 0, Instant.now(), boundedSpec(spec))),
                    candidate -> checkpoint.accept(GenerationOutputCapture.capture(assignment, candidate, usage, true)));
            return GenerationOutputCapture.capture(assignment, outcome, usage, false);
        }
        finally {
            active.remove(assignment.identity(), orchestrator);
        }
    }

    static BooleanSupplier stopAuthoring(BooleanSupplier cancelled, WorkerUsageRecorder usage, Instant authoringDeadline) {
        return () -> cancelled.getAsBoolean() || usage.shouldStopAuthoring() || !Instant.now().isBefore(authoringDeadline);
    }

    @Override
    public boolean requestCancel(ExecutionIdentity identity) {
        GenerationOrchestrator orchestrator = active.get(identity);
        if (orchestrator == null) {
            return false;
        }
        orchestrator.requestCancel();
        return true;
    }

    HyperionGenerationSettings settings(GenerationParameters parameters, ChatModel model) {
        var defaults = model.getOptions();
        var builder = defaults instanceof OpenAiChatOptions openAi ? openAi.mutate() : OpenAiChatOptions.builder();
        // Spring AI's default chat options carry 60s even when the configured HTTP client has a longer timeout.
        builder.timeout(llmClientService.effectiveTimeout(parameters.llmPreset()));
        return new HyperionGenerationSettings(parameters.effortProfile(), null, parameters.maxTurns(), parameters.maxDuration(), parameters.maxTokens(),
                parameters.stagedGeneration(), parameters.stagedContext(), parameters.contextWindowTokens(), parameters.llmPreset(), builder.build(), false, true);
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
