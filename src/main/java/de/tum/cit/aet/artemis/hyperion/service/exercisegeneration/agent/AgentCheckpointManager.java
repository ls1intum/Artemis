package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentCheckpointMessageCodec.RecordedMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.WorkspaceArchive;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Opt-in, development-only time travel for the exercise authoring loop. It records the exact prompt, continuation locals, tool authority, and agent-observable sandbox roots before
 * and after each logical authoring call. A replay injects the recorded post-state without calling the provider or re-executing tools; a fork replays earlier calls, restores the
 * selected pre-state, and resumes live from there.
 * <p>
 * With no checkpoint directory and no replay source this class is a strict no-op: no prompt conversion, hashing, sandbox calls, or file IO occur.
 */
@Lazy
@Component
@Conditional(HyperionExerciseGenerationEnabled.class)
public class AgentCheckpointManager {

    private static final Logger log = LoggerFactory.getLogger(AgentCheckpointManager.class);

    private static final int SCHEMA_VERSION = 1;

    private static final DateTimeFormatter RUN_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    // Only agent-owned mutable state belongs in time travel. /opt/hyperion is verifier-owned and contains fresh JUnit reports whose timestamps differ on every readiness run;
    // recording it made otherwise identical forks fail before call 1. The immutable verifier and readiness fixture are part of the current runtime, not authoring state.
    private static final List<String> SNAPSHOT_ROOTS = List.of("/workspace", "/tmp/hyperion");

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    private static final String FORK_INSTRUCTION_PREFIX = "CHECKPOINT FORK EXPERIMENT INSTRUCTION (applies from this call onward):\n";

    private final ObjectMapper objectMapper;

    private final TempFileUtilService tempFileUtilService;

    private final String checkpointDirectory;

    private final String replaySource;

    private final int forkAt;

    private final int forkReviewAt;

    private final boolean strict;

    private final String forkInstruction;

    private final ThreadLocal<RunScope> currentRun = new ThreadLocal<>();

    @Autowired
    public AgentCheckpointManager(ObjectMapper objectMapper, TempFileUtilService tempFileUtilService,
            @Value("${artemis.hyperion.agent.checkpoint-dir:}") String checkpointDirectory, @Value("${artemis.hyperion.agent.checkpoint-replay-from:}") String replaySource,
            @Value("${artemis.hyperion.agent.checkpoint-fork-at:0}") int forkAt, @Value("${artemis.hyperion.agent.checkpoint-fork-review-at:0}") int forkReviewAt,
            @Value("${artemis.hyperion.agent.checkpoint-strict:false}") boolean strict, @Value("${artemis.hyperion.agent.checkpoint-fork-instruction:}") String forkInstruction) {
        this.objectMapper = objectMapper;
        this.tempFileUtilService = tempFileUtilService;
        this.checkpointDirectory = strip(checkpointDirectory);
        this.replaySource = strip(replaySource);
        this.forkAt = forkAt;
        this.forkReviewAt = forkReviewAt;
        this.strict = strict;
        this.forkInstruction = strip(forkInstruction);
        if (forkAt < 0 || forkReviewAt < 0) {
            throw new IllegalArgumentException("checkpoint fork ordinals cannot be negative");
        }
        if (forkAt > 0 && forkReviewAt > 0) {
            throw new IllegalArgumentException("checkpoint-fork-at and checkpoint-fork-review-at are mutually exclusive");
        }
        if (hasFork() && this.replaySource.isBlank()) {
            throw new IllegalArgumentException("a checkpoint fork requires checkpoint-replay-from");
        }
        if (!this.forkInstruction.isBlank() && forkAt == 0) {
            throw new IllegalArgumentException("checkpoint-fork-instruction is supported only for an author call fork");
        }
        SECRET_MATERIAL_POLICY.requireSafe("checkpoint/fork-instruction", this.forkInstruction.getBytes(StandardCharsets.UTF_8),
                HyperionSecretMaterialPolicy.Origin.PROVIDER_PROMPT);
    }

    public AgentCheckpointManager(ObjectMapper objectMapper, String checkpointDirectory, String replaySource, int forkAt, int forkReviewAt, boolean strict,
            String forkInstruction) {
        this(objectMapper, new TempFileUtilService(Path.of(System.getProperty("java.io.tmpdir"))), checkpointDirectory, replaySource, forkAt, forkReviewAt, strict,
                forkInstruction);
    }

    public AgentCheckpointManager(ObjectMapper objectMapper, String checkpointDirectory, String replaySource, int forkAt, boolean strict, String forkInstruction) {
        this(objectMapper, checkpointDirectory, replaySource, forkAt, 0, strict, forkInstruction);
    }

    private boolean hasFork() {
        return forkAt > 0 || forkReviewAt > 0;
    }

    public boolean enabled() {
        return !checkpointDirectory.isBlank() || !replaySource.isBlank();
    }

    /**
     * Fingerprints the provider contract a checkpoint was taken under, so a recorded turn can never be replayed against an incompatible configuration.
     * <p>
     * The fingerprint covers the options the run actually sends, not the model bean's defaults: once an effort profile can pin a different model, context window, or decoding
     * parameter, defaults-only fingerprinting would make a checkpoint taken under one profile replayable under another. The profile name is included as well, so two profiles
     * remain distinguishable even if their options happen to serialize identically.
     *
     * @param chatModel           the configured provider implementation, or {@code null} when none is configured
     * @param contextWindowTokens the context window this run compacts against
     * @param effectiveOptions    the options every request of this run starts from
     * @param profileName         the resolved effort profile name, or {@code ""} for the deployment default
     * @return the contract string, or {@code ""} when checkpointing is disabled
     */
    String providerContract(@Nullable ChatModel chatModel, int contextWindowTokens, @Nullable ChatOptions effectiveOptions, String profileName) {
        return providerContract(chatModel, contextWindowTokens, effectiveOptions, profileName, null, null);
    }

    String providerContract(@Nullable ChatModel chatModel, int contextWindowTokens, @Nullable ChatOptions effectiveOptions, String profileName, @Nullable Long maxTokensPerJob,
            @Nullable Duration maxJobDuration) {
        if (!enabled() || chatModel == null) {
            return "";
        }
        try {
            ObjectMapper canonicalMapper = objectMapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            return chatModel.getClass().getName() + "\nprofile=" + profileName + "\ncontextWindow=" + contextWindowTokens + "\nmaxTokensPerJob=" + maxTokensPerJob
                    + "\nmaxJobDuration=" + maxJobDuration + "\noptions=" + canonicalMapper.writeValueAsString(effectiveOptions);
        }
        catch (IOException e) {
            throw new IllegalStateException("The configured provider options cannot be fingerprinted safely for checkpointing.", e);
        }
    }

    String toolContract(ToolCallback[] callbacks) {
        return java.util.Arrays.stream(callbacks).map(ToolCallback::getToolDefinition)
                .map(definition -> definition.name() + "\n" + definition.description() + "\n" + definition.inputSchema()).sorted()
                .collect(java.util.stream.Collectors.joining("\n---\n"));
    }

    /**
     * Starts one run-scoped ordinal and binds it to the current generation thread.
     *
     * @param jobId         generation job identifier
     * @param exercise      exercise whose setup becomes replay provenance
     * @param tools         live sandbox tools to snapshot and restore
     * @param approvedSpecs server-side approved-specification authority
     */
    public void beginRun(String jobId, ProgrammingExercise exercise, SandboxAgentTools tools, ApprovedSpecRegistry approvedSpecs) {
        if (!enabled()) {
            return;
        }
        if (currentRun.get() != null) {
            throw new IllegalStateException("A Hyperion checkpoint run is already active on this thread.");
        }
        try {
            Path source = replaySource.isBlank() ? null : Path.of(replaySource).toAbsolutePath().normalize();
            RunManifest sourceManifest = source == null ? null : requireCompatibleRun(source, exercise);
            Path output = outputDirectory(jobId, source);
            if (output != null) {
                Files.createDirectories(output.resolve("calls"));
                Files.createDirectories(output.resolve("reviews"));
                Files.createDirectories(output.resolve("blobs"));
                writeAtomic(output.resolve("run.json"),
                        new RunManifest(SCHEMA_VERSION, jobId, exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getPackageName(),
                                exercise.getProblemStatement(), exercise.getProgrammingLanguage() == null ? null : exercise.getProgrammingLanguage().name(),
                                exercise.getProjectType() == null ? null : exercise.getProjectType().name(), Instant.now(), source == null ? null : source.toString(), forkAt,
                                forkReviewAt, false));
            }
            currentRun.set(new RunScope(jobId, exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getPackageName(), exercise.getProblemStatement(),
                    exercise.getProgrammingLanguage() == null ? null : exercise.getProgrammingLanguage().name(),
                    exercise.getProjectType() == null ? null : exercise.getProjectType().name(), tools, approvedSpecs, source, sourceManifest, output));
            log.info("Hyperion agent checkpoints enabled for job {} ({})", jobId, output == null ? "replay only" : output);
        }
        catch (IOException | RuntimeException e) {
            handleFailure("Could not start the checkpoint run", e);
        }
    }

    /** Completes the current thread's run manifest and releases its state. */
    public void endRun() {
        RunScope run = currentRun.get();
        currentRun.remove();
        if (run != null && !run.failed && forkReviewAt > 0 && !run.liveSuffix) {
            run.failed = true;
            throw new IllegalStateException("Checkpoint has no reachable reviewer call r" + forkReviewAt + ".");
        }
        if (run == null || run.outputDirectory == null || run.failed) {
            return;
        }
        try {
            writeAtomic(run.outputDirectory.resolve("run.json"),
                    new RunManifest(SCHEMA_VERSION, run.jobId, run.exerciseId, run.exerciseTitle, run.exerciseShortName, run.exercisePackageName, run.exerciseProblemStatement,
                            run.programmingLanguage, run.projectType, run.startedAt, run.sourceDirectory == null ? null : run.sourceDirectory.toString(), forkAt, forkReviewAt,
                            true));
        }
        catch (IOException | RuntimeException e) {
            handleFailure("Could not complete the checkpoint run", e);
        }
    }

    /**
     * @return whether the current run is a provider-free full replay rather than a live fork
     */
    public boolean replaysAllAuthoringCalls() {
        return !replaySource.isBlank() && !hasFork() && currentRun.get() != null;
    }

    /**
     * Records or replays a tool-free reviewer call. Reviewer calls have their own ordinal because they occur between authoring turns; keeping both sequences explicit makes a
     * changed fork free to add or remove reviews without corrupting author-turn addressing.
     *
     * @param systemPrompt exact rendered reviewer system prompt
     * @param userPrompt   exact reviewer input
     * @param contract     provider model and token-option contract
     * @param liveCall     provider call used while recording or after a fork
     * @return the live or replayed response, possibly {@code null}
     */
    @Nullable
    public String reviewerCall(String systemPrompt, String userPrompt, String contract, Supplier<@Nullable String> liveCall) {
        RunScope run = currentRun.get();
        if (run == null || run.failed) {
            return liveCall.get();
        }
        int ordinal = ++run.reviewerOrdinal;
        boolean selectedReviewerFork = run.sourceDirectory != null && forkReviewAt == ordinal && !run.liveSuffix;
        boolean replay = run.sourceDirectory != null && !run.liveSuffix && !selectedReviewerFork && (forkReviewAt > 0 || forkAt == 0 || run.ordinal < forkAt);
        if (replay) {
            ReviewerRecord source;
            try {
                source = readReviewer(run.sourceDirectory, ordinal);
                if (!source.systemPrompt().equals(systemPrompt) || !source.userPrompt().equals(userPrompt) || !source.contract().equals(contract)) {
                    throw new IllegalStateException("Reviewer prompt or provider contract drift before review " + ordinal + "; fork at the preceding author call.");
                }
            }
            catch (IOException | RuntimeException e) {
                handleTurnFailure(run, "Could not checkpoint reviewer call " + ordinal, e);
                return null;
            }
            persistReviewerRecord(run, source);
            if (source.errorClass() != null) {
                throw new RecordedReviewerException(source.errorClass() + ": " + source.errorMessage());
            }
            return source.response();
        }
        if (selectedReviewerFork) {
            try {
                ReviewerRecord source = readReviewer(run.sourceDirectory, ordinal);
                if (!source.contract().equals(contract)) {
                    throw new IllegalStateException("Provider contract drift at selected reviewer fork r" + ordinal + ".");
                }
                run.liveSuffix = true;
            }
            catch (IOException | RuntimeException e) {
                handleTurnFailure(run, "Could not prepare reviewer fork r" + ordinal, e);
                return null;
            }
        }
        String response;
        try {
            response = liveCall.get();
        }
        catch (RuntimeException failure) {
            persistReviewer(run, ordinal, systemPrompt, userPrompt, contract, null, failure);
            throw failure;
        }
        persistReviewer(run, ordinal, systemPrompt, userPrompt, contract, response, null);
        return response;
    }

    private void persistReviewer(RunScope run, int ordinal, String systemPrompt, String userPrompt, String contract, @Nullable String response,
            @Nullable RuntimeException failure) {
        persistReviewerRecord(run, new ReviewerRecord(SCHEMA_VERSION, ordinal, systemPrompt, userPrompt, contract, response, failure == null ? null : failure.getClass().getName(),
                failure == null ? null : failure.getMessage()));
    }

    private void persistReviewerRecord(RunScope run, ReviewerRecord record) {
        try {
            if (run.outputDirectory != null) {
                writeReviewerAtomic(reviewerPath(run.outputDirectory, record.ordinal()), record);
            }
        }
        catch (IOException | RuntimeException e) {
            handleTurnFailure(run, "Could not checkpoint reviewer call " + record.ordinal(), e);
        }
    }

    private RunManifest requireCompatibleRun(Path source, ProgrammingExercise exercise) throws IOException {
        Path manifestPath = source.resolve("run.json");
        if (!Files.isRegularFile(manifestPath)) {
            throw new IllegalStateException("Checkpoint source has no run manifest: " + manifestPath);
        }
        RunManifest manifest = objectMapper.readValue(manifestPath.toFile(), RunManifest.class);
        String language = exercise.getProgrammingLanguage() == null ? null : exercise.getProgrammingLanguage().name();
        String projectType = exercise.getProjectType() == null ? null : exercise.getProjectType().name();
        if (manifest.schemaVersion() != SCHEMA_VERSION || !manifest.completed()) {
            throw new IllegalStateException("Checkpoint source is incomplete or uses an incompatible schema.");
        }
        if (!Objects.equals(manifest.exerciseTitle(), exercise.getTitle()) || !Objects.equals(manifest.exerciseShortName(), exercise.getShortName())
                || !Objects.equals(manifest.exercisePackageName(), exercise.getPackageName())
                || !Objects.equals(manifest.exerciseProblemStatement(), exercise.getProblemStatement()) || !Objects.equals(manifest.programmingLanguage(), language)
                || !Objects.equals(manifest.projectType(), projectType)) {
            throw new IllegalStateException("Checkpoint exercise setup differs from the resumed exercise. Recreate it with the title, short name, package, language, and project "
                    + "type recorded in run.json.");
        }
        return manifest;
    }

    TurnHandle beforeTurn(int localTurn, int maxTurns, String providerContract, String toolContract, List<Message> conversation, LoopCursor cursor) {
        RunScope run = currentRun.get();
        if (run == null || run.failed) {
            return TurnHandle.disabled();
        }
        int ordinal = ++run.ordinal;
        try {
            CheckpointState current = captureState(run, conversation, cursor);
            boolean prepared = false;
            boolean replayBeforeReviewerFork = run.sourceDirectory != null && forkReviewAt > 0 && !run.liveSuffix;
            if (run.sourceDirectory != null && (replayBeforeReviewerFork || !hasFork() || ordinal <= forkAt)) {
                TurnRecord sourceTurn = readTurn(run.sourceDirectory, ordinal);
                boolean selectedAuthorFork = forkAt > 0 && ordinal == forkAt;
                CheckpointState replayAnchor = replayExpectedState(run, sourceTurn, ordinal);
                requireCompatible(sourceTurn, replayAnchor, current, providerContract, toolContract, localTurn, maxTurns, ordinal, selectedAuthorFork);
                if (!selectedAuthorFork) {
                    if (sourceTurn.after() == null) {
                        throw new IllegalStateException("Checkpoint call " + ordinal + " has no committed post-state.");
                    }
                    persistReplayedTurn(run, withReplayAnchor(sourceTurn, replayAnchor));
                    restoreState(run, sourceTurn.after());
                    return TurnHandle.replayed(ordinal, sourceTurn);
                }
                if (selectedAuthorFork) {
                    run.liveSuffix = true;
                    restoreState(run, sourceTurn.before());
                    if (!forkInstruction.isBlank()) {
                        List<Message> preparedConversation = new ArrayList<>(AgentCheckpointMessageCodec.decode(sourceTurn.before().conversation()));
                        preparedConversation.add(new UserMessage(FORK_INSTRUCTION_PREFIX + forkInstruction));
                        current = captureState(run, preparedConversation, sourceTurn.before().cursor());
                        prepared = true;
                    }
                    else {
                        current = captureState(run, conversation, cursor);
                    }
                    return new TurnHandle(true, false, prepared, ordinal, providerContract, toolContract, localTurn, maxTurns, replayAnchor, current, null);
                }
            }
            return new TurnHandle(true, false, prepared, ordinal, providerContract, toolContract, localTurn, maxTurns, null, current, null);
        }
        catch (IOException | RuntimeException e) {
            return handleTurnFailure(run, "Could not prepare checkpoint call " + ordinal, e);
        }
    }

    void finishTurn(TurnHandle handle, List<Message> conversation, LoopCursor cursor, AgentLoopResult.Status terminalStatus) {
        RunScope run = currentRun.get();
        if (run == null || run.failed || !handle.enabled() || handle.replayed()) {
            return;
        }
        try {
            CheckpointState after = captureState(run, conversation, cursor);
            TurnRecord record = new TurnRecord(SCHEMA_VERSION, handle.ordinal(), handle.localTurn(), handle.maxTurns(), handle.providerContract(), handle.toolContract(),
                    handle.before(), after, terminalStatus == null ? null : terminalStatus.name(), handle.replayAnchor());
            if (run.outputDirectory != null) {
                writeTurnAtomic(callPath(run.outputDirectory, handle.ordinal()), record);
            }
        }
        catch (IOException | RuntimeException e) {
            handleTurnFailure(run, "Could not commit checkpoint call " + handle.ordinal(), e);
        }
    }

    private CheckpointState captureState(RunScope run, List<Message> conversation, LoopCursor cursor) throws IOException {
        List<RecordedMessage> recordedMessages = AgentCheckpointMessageCodec.encode(conversation);
        Map<String, RootSnapshot> roots = new LinkedHashMap<>();
        for (String root : SNAPSHOT_ROOTS) {
            RootSnapshot snapshot = captureRoot(run, root);
            if (snapshot != null) {
                roots.put(root, snapshot);
            }
        }
        if (strict) {
            for (String root : SNAPSHOT_ROOTS) {
                RootSnapshot repeated = captureRoot(run, root);
                RootSnapshot first = roots.get(root);
                if (first == null ? repeated != null : repeated == null || !first.sha256().equals(repeated.sha256())) {
                    throw new IllegalStateException("Sandbox root changed while checkpointing " + root + "; a background process makes this call non-forkable.");
                }
            }
        }
        String approvedSpec = run.approvedSpecs.approved(run.tools.checkpointSessionId()).orElse(null);
        return new CheckpointState(recordedMessages, cursor, run.tools.checkpointState(), Map.copyOf(roots), approvedSpec);
    }

    @Nullable
    private RootSnapshot captureRoot(RunScope run, String root) throws IOException {
        InteractiveSandbox sandbox = run.tools.checkpointSandbox();
        String sessionId = run.tools.checkpointSessionId();
        SandboxExecResultDTO exists = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "sh", "-c", "test -e '" + root + "'");
        if (!exists.isSuccess()) {
            run.rootCache.remove(root);
            return null;
        }
        SandboxExecResultDTO fingerprint = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "sh", "-c",
                "cd '" + root + "' && { find . -mindepth 1 -printf '%y %m %P\\0' | sort -z; find . -type f -print0 | sort -z | xargs -0 -r sha256sum; } | sha256sum");
        if (!fingerprint.isSuccess()) {
            throw new IllegalStateException("Could not fingerprint checkpoint root " + root + ": " + fingerprint.combinedOutput());
        }
        String signature = fingerprint.stdout().strip();
        CachedRoot cached = run.rootCache.get(root);
        if (!signature.isEmpty() && cached != null && cached.signature().equals(signature)) {
            return cached.snapshot();
        }
        String prefix = Path.of(root).getFileName().toString();
        WorkspaceArchive.BinaryArchiveContents contents;
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, root)) {
            contents = WorkspaceArchive.readBinaryTarContents(tar, prefix);
        }
        List<SnapshotFile> files = new ArrayList<>(contents.files().size());
        for (Map.Entry<String, byte[]> entry : contents.files().entrySet()) {
            String digest = sha256(entry.getValue());
            writeBlob(run, digest, entry.getValue());
            files.add(new SnapshotFile(entry.getKey(), digest, contents.modes().getOrDefault(entry.getKey(), 0644)));
        }
        files.sort(java.util.Comparator.comparing(SnapshotFile::path));
        List<SnapshotDirectory> directories = contents.directories().entrySet().stream().map(entry -> new SnapshotDirectory(entry.getKey(), entry.getValue()))
                .sorted(java.util.Comparator.comparing(SnapshotDirectory::path)).toList();
        RootSnapshot snapshot = new RootSnapshot(files, directories, sha256(objectMapper.writeValueAsBytes(List.of(files, directories))));
        if (!signature.isEmpty()) {
            run.rootCache.put(root, new CachedRoot(signature, snapshot));
        }
        return snapshot;
    }

    private void restoreState(RunScope run, CheckpointState state) throws IOException {
        InteractiveSandbox sandbox = run.tools.checkpointSandbox();
        String sessionId = run.tools.checkpointSessionId();
        for (String root : SNAPSHOT_ROOTS) {
            SandboxExecResultDTO cleared = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "sh", "-c",
                    "mkdir -p '" + root + "' && find '" + root + "' -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +");
            if (!cleared.isSuccess()) {
                throw new IllegalStateException("Could not clear checkpoint root " + root + ": " + cleared.combinedOutput());
            }
            RootSnapshot snapshot = state.roots().get(root);
            if (snapshot == null || snapshot.files().isEmpty()) {
                continue;
            }
            Map<String, byte[]> files = new LinkedHashMap<>();
            Map<String, Integer> modes = new LinkedHashMap<>();
            Map<String, Integer> directories = new LinkedHashMap<>();
            for (SnapshotFile file : snapshot.files()) {
                byte[] content = readBlob(run, file.sha256());
                files.put(file.path(), content);
                modes.put(file.path(), file.mode());
            }
            for (SnapshotDirectory directory : snapshot.directories()) {
                directories.put(directory.path(), directory.mode());
            }
            sandbox.copyIn(sessionId, root, WorkspaceArchive.buildBinaryFilesTarStream(files, modes, directories));
        }
        run.tools.restoreCheckpointState(state.tools());
        if (state.approvedSpec() != null) {
            run.approvedSpecs.approve(sessionId, state.approvedSpec());
        }
    }

    private void requireCompatible(TurnRecord source, CheckpointState expected, CheckpointState current, String providerContract, String toolContract, int localTurn, int maxTurns,
            int ordinal, boolean forking) {
        if (source.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalStateException("Checkpoint schema " + source.schemaVersion() + " is incompatible with " + SCHEMA_VERSION + ".");
        }
        if (!source.toolContract().equals(toolContract)) {
            throw new IllegalStateException("Tool contract changed before checkpoint call " + ordinal + "; refusing an unsafe replay/fork.");
        }
        if (hasFork() && !source.providerContract().equals(providerContract)) {
            throw new IllegalStateException("Provider model or options changed before checkpoint call " + ordinal + "; refusing an incomparable fork.");
        }
        if (source.localTurn() != localTurn || source.maxTurns() != maxTurns) {
            throw new IllegalStateException("Loop cursor changed before checkpoint call " + ordinal + " (expected turn " + source.localTurn() + "/" + source.maxTurns() + ", got "
                    + localTurn + "/" + maxTurns + ").");
        }
        if (!forking) {
            // RecordedMessage is the canonical provider contract. Compare it structurally: metadata maps are unordered, so hashing their incidental JSON key order creates
            // false drift after an encode/decode replay cycle.
            if (!objectMapper.valueToTree(expected.conversation()).equals(objectMapper.valueToTree(current.conversation()))) {
                throw new IllegalStateException("Prompt drift before replayed checkpoint call " + ordinal + ". Use a fork at the first intentionally changed call.");
            }
            if (!expected.cursor().equals(current.cursor()) || !expected.tools().equals(current.tools()) || !Objects.equals(expected.approvedSpec(), current.approvedSpec())) {
                throw new IllegalStateException("Agent continuation state drift before replayed checkpoint call " + ordinal + "; refusing to hide a non-deterministic prefix.");
            }
            Map<String, String> expectedRoots = rootHashes(expected);
            Map<String, String> actualRoots = rootHashes(current);
            if (!expectedRoots.equals(actualRoots)) {
                Set<String> roots = new LinkedHashSet<>(expectedRoots.keySet());
                roots.addAll(actualRoots.keySet());
                List<String> changedRoots = roots.stream().filter(root -> !Objects.equals(expectedRoots.get(root), actualRoots.get(root))).toList();
                throw new IllegalStateException(
                        "Sandbox state drift before replayed checkpoint call " + ordinal + " in " + changedRoots + "; refusing to hide a non-deterministic prefix.");
            }
        }
    }

    private static CheckpointState replayExpectedState(RunScope run, TurnRecord source, int ordinal) {
        if (source.replayAnchor() != null) {
            return source.replayAnchor();
        }
        RunManifest manifest = run.sourceManifest;
        if (manifest == null || manifest.parent() == null || manifest.forkAt() != ordinal || source.before().conversation().isEmpty()) {
            return source.before();
        }
        List<RecordedMessage> conversation = source.before().conversation();
        int anchorSize = conversation.size();
        while (anchorSize > 0) {
            RecordedMessage last = conversation.get(anchorSize - 1);
            if (!"user".equals(last.role()) || last.text() == null || !last.text().startsWith(FORK_INSTRUCTION_PREFIX)) {
                break;
            }
            anchorSize--;
        }
        if (anchorSize == conversation.size()) {
            return source.before();
        }
        return new CheckpointState(List.copyOf(conversation.subList(0, anchorSize)), source.before().cursor(), source.before().tools(), source.before().roots(),
                source.before().approvedSpec());
    }

    private static TurnRecord withReplayAnchor(TurnRecord source, CheckpointState replayAnchor) {
        if (source.replayAnchor() != null || replayAnchor == source.before()) {
            return source;
        }
        return new TurnRecord(source.schemaVersion(), source.ordinal(), source.localTurn(), source.maxTurns(), source.providerContract(), source.toolContract(), source.before(),
                source.after(), source.terminalStatus(), replayAnchor);
    }

    private static Map<String, String> rootHashes(CheckpointState state) {
        Map<String, String> hashes = new LinkedHashMap<>();
        SNAPSHOT_ROOTS.forEach(root -> {
            RootSnapshot snapshot = state.roots().get(root);
            // Old recordings omitted roots that did not exist yet; a restored empty directory is the same authoring state.
            if (snapshot != null && (!snapshot.files().isEmpty() || !snapshot.directories().isEmpty())) {
                hashes.put(root, snapshot.sha256());
            }
        });
        return hashes;
    }

    private void writeBlob(RunScope run, String digest, byte[] bytes) throws IOException {
        if (run.outputDirectory == null) {
            return;
        }
        Path blob = run.outputDirectory.resolve("blobs").resolve(digest);
        if (!Files.exists(blob)) {
            writeAtomicBytes(blob, bytes);
        }
    }

    private void persistReplayedTurn(RunScope run, TurnRecord sourceTurn) throws IOException {
        if (run.outputDirectory == null) {
            return;
        }
        copyStateBlobs(run, sourceTurn.before());
        if (sourceTurn.after() != null) {
            copyStateBlobs(run, sourceTurn.after());
        }
        writeTurnAtomic(callPath(run.outputDirectory, sourceTurn.ordinal()), sourceTurn);
    }

    private void copyStateBlobs(RunScope run, CheckpointState state) throws IOException {
        for (RootSnapshot root : state.roots().values()) {
            for (SnapshotFile file : root.files()) {
                writeBlob(run, file.sha256(), readBlob(run, file.sha256()));
            }
        }
    }

    private byte[] readBlob(RunScope run, String digest) throws IOException {
        Path primary = run.sourceDirectory == null ? null : run.sourceDirectory.resolve("blobs").resolve(digest);
        Path fallback = run.outputDirectory == null ? null : run.outputDirectory.resolve("blobs").resolve(digest);
        Path blob = primary != null && Files.isRegularFile(primary) ? primary : fallback;
        if (blob == null || !Files.isRegularFile(blob)) {
            throw new IllegalStateException("Checkpoint blob is missing: " + digest);
        }
        byte[] bytes = Files.readAllBytes(blob);
        if (!sha256(bytes).equals(digest)) {
            throw new IllegalStateException("Checkpoint blob failed its integrity check: " + digest);
        }
        return bytes;
    }

    private TurnRecord readTurn(Path source, int ordinal) throws IOException {
        Path path = callPath(source, ordinal);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Checkpoint has no call " + ordinal + " at " + path);
        }
        byte[] bytes = Files.readAllBytes(path);
        Path checksumPath = checksumPath(path);
        if (!Files.isRegularFile(checksumPath) || !Files.readString(checksumPath).strip().equals(sha256(bytes))) {
            throw new IllegalStateException("Checkpoint call " + ordinal + " failed its integrity check.");
        }
        return objectMapper.readValue(bytes, TurnRecord.class);
    }

    private ReviewerRecord readReviewer(Path source, int ordinal) throws IOException {
        Path path = reviewerPath(source, ordinal);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Checkpoint has no reviewer call " + ordinal + " at " + path);
        }
        byte[] bytes = readChecked(path, "Reviewer call " + ordinal);
        ReviewerRecord record = objectMapper.readValue(bytes, ReviewerRecord.class);
        if (record.schemaVersion() != SCHEMA_VERSION || record.ordinal() != ordinal) {
            throw new IllegalStateException("Reviewer call " + ordinal + " uses an incompatible schema or ordinal.");
        }
        return record;
    }

    private Path outputDirectory(String jobId, @Nullable Path source) {
        String root = checkpointDirectory;
        if (root.isBlank() && source != null && hasFork()) {
            root = source.resolveSibling("branches").toString();
        }
        if (root.isBlank()) {
            return null;
        }
        String safeJob = jobId.replaceAll("[^a-zA-Z0-9._-]", "-");
        return Path.of(root).toAbsolutePath().normalize().resolve(RUN_TIMESTAMP.format(Instant.now()) + "-" + safeJob);
    }

    private static Path callPath(Path runDirectory, int ordinal) {
        return runDirectory.resolve("calls").resolve("%06d.json".formatted(ordinal));
    }

    private static Path reviewerPath(Path runDirectory, int ordinal) {
        return runDirectory.resolve("reviews").resolve("%06d.json".formatted(ordinal));
    }

    private void writeAtomic(Path target, Object value) throws IOException {
        writeAtomicBytes(target, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
    }

    private void writeTurnAtomic(Path target, TurnRecord value) throws IOException {
        writeCheckedAtomic(target, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
    }

    private void writeReviewerAtomic(Path target, ReviewerRecord value) throws IOException {
        writeCheckedAtomic(target, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
    }

    private void writeCheckedAtomic(Path target, byte[] bytes) throws IOException {
        writeAtomicBytes(target, bytes);
        writeAtomicBytes(checksumPath(target), (sha256(bytes) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] readChecked(Path path, String label) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        Path checksumPath = checksumPath(path);
        if (!Files.isRegularFile(checksumPath) || !Files.readString(checksumPath).strip().equals(sha256(bytes))) {
            throw new IllegalStateException(label + " failed its integrity check.");
        }
        return bytes;
    }

    private static Path checksumPath(Path turnPath) {
        return turnPath.resolveSibling(turnPath.getFileName() + ".sha256");
    }

    private void writeAtomicBytes(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = tempFileUtilService.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    private TurnHandle handleTurnFailure(RunScope run, String message, Exception failure) {
        run.failed = true;
        if (strict || run.sourceDirectory != null) {
            throw failure instanceof RuntimeException runtime ? runtime : new IllegalStateException(message, failure);
        }
        log.warn("{}: {}", message, failure.getMessage());
        return TurnHandle.disabled();
    }

    private void handleFailure(String message, Exception failure) {
        if (strict || !replaySource.isBlank()) {
            throw failure instanceof RuntimeException runtime ? runtime : new IllegalStateException(message, failure);
        }
        log.warn("{}: {}", message, failure.getMessage());
    }

    private static String strip(@Nullable String value) {
        return value == null ? "" : value.strip();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    record LoopCursor(String lastAssistantText, int consecutiveToolFailures, long lastPromptTokens, int messagesAtLastCall) {
    }

    record SnapshotFile(String path, String sha256, int mode) {
    }

    record SnapshotDirectory(String path, int mode) {
    }

    record RootSnapshot(List<SnapshotFile> files, List<SnapshotDirectory> directories, String sha256) {
    }

    private record CachedRoot(String signature, RootSnapshot snapshot) {
    }

    record CheckpointState(List<RecordedMessage> conversation, LoopCursor cursor, SandboxAgentTools.CheckpointState tools, Map<String, RootSnapshot> roots,
            @Nullable String approvedSpec) {
    }

    record TurnRecord(int schemaVersion, int ordinal, int localTurn, int maxTurns, String providerContract, String toolContract, CheckpointState before,
            @Nullable CheckpointState after, @Nullable String terminalStatus, @Nullable CheckpointState replayAnchor) {
    }

    record ReviewerRecord(int schemaVersion, int ordinal, String systemPrompt, String userPrompt, String contract, @Nullable String response, @Nullable String errorClass,
            @Nullable String errorMessage) {
    }

    private static final class RecordedReviewerException extends RuntimeException {

        private RecordedReviewerException(String message) {
            super(message);
        }
    }

    record RunManifest(int schemaVersion, String jobId, @Nullable Long exerciseId, @Nullable String exerciseTitle, @Nullable String exerciseShortName,
            @Nullable String exercisePackageName, @Nullable String exerciseProblemStatement, @Nullable String programmingLanguage, @Nullable String projectType, Instant startedAt,
            @Nullable String parent, int forkAt, int forkReviewAt, boolean completed) {
    }

    record TurnHandle(boolean enabled, boolean replayed, boolean prepared, int ordinal, String providerContract, String toolContract, int localTurn, int maxTurns,
            @Nullable CheckpointState replayAnchor, @Nullable CheckpointState before, @Nullable TurnRecord source) {

        static TurnHandle disabled() {
            return new TurnHandle(false, false, false, 0, "", "", 0, 0, null, null, null);
        }

        static TurnHandle replayed(int ordinal, TurnRecord source) {
            return new TurnHandle(true, true, false, ordinal, source.providerContract(), source.toolContract(), source.localTurn(), source.maxTurns(), source.replayAnchor(),
                    source.before(), source);
        }

        CheckpointState replayedAfter() {
            if (!replayed || source == null || source.after() == null) {
                throw new IllegalStateException("This checkpoint handle has no replayed post-state.");
            }
            return source.after();
        }

        AgentLoopResult.Status replayedTerminalStatus() {
            return source == null || source.terminalStatus() == null ? null : AgentLoopResult.Status.valueOf(source.terminalStatus());
        }
    }

    private static final class RunScope {

        private final String jobId;

        @Nullable
        private final Long exerciseId;

        @Nullable
        private final String exerciseTitle;

        @Nullable
        private final String exerciseShortName;

        @Nullable
        private final String exercisePackageName;

        @Nullable
        private final String exerciseProblemStatement;

        @Nullable
        private final String programmingLanguage;

        @Nullable
        private final String projectType;

        private final SandboxAgentTools tools;

        private final ApprovedSpecRegistry approvedSpecs;

        @Nullable
        private final Path sourceDirectory;

        @Nullable
        private final RunManifest sourceManifest;

        @Nullable
        private final Path outputDirectory;

        private final Instant startedAt = Instant.now();

        private int ordinal;

        private int reviewerOrdinal;

        private final Map<String, CachedRoot> rootCache = new LinkedHashMap<>();

        private boolean failed;

        private boolean liveSuffix;

        private RunScope(String jobId, @Nullable Long exerciseId, @Nullable String exerciseTitle, @Nullable String exerciseShortName, @Nullable String exercisePackageName,
                @Nullable String exerciseProblemStatement, @Nullable String programmingLanguage, @Nullable String projectType, SandboxAgentTools tools,
                ApprovedSpecRegistry approvedSpecs, @Nullable Path sourceDirectory, @Nullable RunManifest sourceManifest, @Nullable Path outputDirectory) {
            this.jobId = jobId;
            this.exerciseId = exerciseId;
            this.exerciseTitle = exerciseTitle;
            this.exerciseShortName = exerciseShortName;
            this.exercisePackageName = exercisePackageName;
            this.exerciseProblemStatement = exerciseProblemStatement;
            this.programmingLanguage = programmingLanguage;
            this.projectType = projectType;
            this.tools = tools;
            this.approvedSpecs = approvedSpecs;
            this.sourceDirectory = sourceDirectory;
            this.sourceManifest = sourceManifest;
            this.outputDirectory = outputDirectory;
        }
    }
}
