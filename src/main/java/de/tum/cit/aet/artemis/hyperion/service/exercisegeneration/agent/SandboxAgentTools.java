package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ExerciseIntegrityGate;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SeededStructuralTests;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * The file, shell, and verification tools the exercise-generation agent calls, bound to one sandbox session. Created per session (it holds the session id), so not a Spring bean.
 * <p>
 * Handing the agent a full shell is safe because correctness is never judged from what these tools report. In a staged session, {@code verify} and the gating half of
 * {@code submit} delegate to {@link StageCheckService} for the current stage's check, as cheap as that stage allows; in an unstaged session {@code verify} runs the same
 * differential as the post-loop verifier. Either way the result is advisory — the post-loop verifier decides mechanical validity.
 * <p>
 * Nothing here is synchronized: one session's tool calls are serial, and the volatile fields exist only because the orchestrator may hand a stage over from a different thread
 * between two of them.
 */
public class SandboxAgentTools implements SubmitVetoAware {

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    private static final String WORKSPACE = "/workspace";

    private static final Duration BASH_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Soft per-command limit enforced inside the wrapper by coreutils {@code timeout}. It must stay below {@link #BASH_TIMEOUT}: the docker-exec timeout cannot kill just the
     * command, so reaching it destroys the whole sandbox session. Under the soft limit a slow command is stopped in the container, its partial output survives in the spill log,
     * and the session continues. The exec timeout remains only as a backstop for images that ship no {@code timeout}.
     */
    private static final int BASH_SOFT_TIMEOUT_SECONDS = 270;

    /** Spill directory inside the sandbox, outside /workspace so it is never picked up by repository extraction. */
    private static final String SPILL_DIR = "/tmp/hyperion";

    /**
     * Bytes of the output tail returned inline. It must stay under {@link AgentLoopRunner#MAX_TOOL_RESPONSE_CHARS}, or the loop truncates again on top and the marker this tool
     * appends becomes a lie about what was elided. The full output stays in the spill file.
     */
    static final int BASH_TAIL_BYTES = 10_000;

    /**
     * Characters {@code read_file} returns inline per call. Same {@link AgentLoopRunner#MAX_TOOL_RESPONSE_CHARS} constraint as {@link #BASH_TAIL_BYTES}, so the continuation
     * footer names a line that really is next.
     */
    static final int READ_INLINE_MAX_CHARS = 10_000;

    /** Per-command spill-file ceiling via {@code ulimit -f} (512-byte blocks): 65536 * 512 = 32 MB, so a runaway command cannot fill the container disk before the timeout. */
    private static final int SPILL_ULIMIT_BLOCKS = 65_536;

    /** First line the bash wrapper prints, carrying the real exit code and size (the container exec's own exit code reflects the wrapper, not the command). */
    private static final Pattern BASH_META = Pattern.compile("^__HYP_META__ rc=(-?\\d+) bytes=(\\d+) lines=(\\d+)$");

    /**
     * Matches the {@code List.toString()} render of a JSON argv array (e.g. {@code [bash, -lc, ls -R]}), which is what arrives when the model sends {@code {"command":[...]}}
     * instead of a shell string. A POSIX {@code [ -f x ]} test has a space after the bracket and a single-element {@code [foo]} has no comma, so neither is caught.
     */
    private static final Pattern RENDERED_ARGV_ARRAY = Pattern.compile("^\\[\\S.*,.*]$", Pattern.DOTALL);

    private static final String SELF_REPORTED_VERDICT_MARKER = "MECHANICAL PRECHECK:";

    private final InteractiveSandbox sandbox;

    private final String sessionId;

    /** Runs the unstaged {@code verify}, the same differential as the post-loop mechanical gate. */
    @Nullable
    private final DifferentialVerificationService verifier;

    /** Supplies the {@code verify.sh} and SCA configuration the {@code verify} tool's differential runs under. */
    @Nullable
    private final ProgrammingExercise exercise;

    @Nullable
    private final StageCheckService stageCheckService;

    /** The pre-generation tests snapshot, which is what lets the in-loop verifier tell untouched seeded files from files this run authored or changed. */
    private final Map<String, String> seedTestsFiles;

    private final boolean adaptation;

    private int bashSequence = 0;

    private boolean sandboxSessionTerminated;

    /** {@code null} means the session is unstaged, which is what makes {@code verify} and {@code submit} run the full differential instead of one stage's check. */
    private volatile GenerationStage currentStage;

    /** Root artifacts the current semantic repair may change; {@code null} outside a finding-scoped repair. */
    @Nullable
    private volatile Set<String> repairWritableRoots;

    private volatile SeededStructuralTests seededStructuralTests = SeededStructuralTests.EMPTY;

    @Nullable
    private volatile Supplier<SeededStructuralTests> structuralOracleRefresh;

    /** Makes {@link AgentLoopRunner} keep the loop going instead of ending the session on a rejected {@code submit}; cleared by {@link #consumeSubmitVeto()}. */
    private volatile boolean submitVetoed;

    private volatile boolean dirtySinceLastPassingCheck = true;

    @Nullable
    private volatile GenerationStage cachedPassingCheckStage;

    @Nullable
    private volatile StageCheckResult cachedPassingCheck;

    /**
     * The TESTS stage's most recent {@link AgentVerifyReport}, so the STATEMENT stage's binding check can resolve {@code [task]} names without a build of its own. Set
     * opportunistically by an in-loop TESTS check and authoritatively by {@link #recordLastTestsReport}.
     */
    @Nullable
    private volatile AgentVerifyReport lastTestsReport;

    /**
     * Serializable continuation state for a development turn checkpoint. Constructor dependencies and {@link #structuralOracleRefresh} deliberately stay bound to the live run;
     * restoring a callback captured from another sandbox would target the wrong session.
     */
    public record CheckpointState(int bashSequence, boolean sandboxSessionTerminated, @Nullable GenerationStage currentStage, @Nullable Set<String> repairWritableRoots,
            SeededStructuralTests seededStructuralTests, boolean structuralOracleRefreshConfigured, boolean submitVetoed, boolean dirtySinceLastPassingCheck,
            @Nullable GenerationStage cachedPassingCheckStage, @Nullable StageCheckResult cachedPassingCheck, @Nullable AgentVerifyReport lastTestsReport) {
    }

    public SandboxAgentTools(InteractiveSandbox sandbox, String sessionId, DifferentialVerificationService verifier, ProgrammingExercise exercise) {
        this(sandbox, sessionId, verifier, exercise, Map.of(), false, null);
    }

    /**
     * A {@code null} {@code stageCheckService} while a stage is active makes {@code verify} and {@code submit} report the gate as unavailable rather than silently falling back
     * to the unstaged differential, which would answer a different question than the stage asked.
     */
    public SandboxAgentTools(InteractiveSandbox sandbox, String sessionId, DifferentialVerificationService verifier, ProgrammingExercise exercise,
            Map<String, String> seedTestsFiles, boolean adaptation, @Nullable StageCheckService stageCheckService) {
        this.sandbox = sandbox;
        this.sessionId = sessionId;
        this.verifier = verifier;
        this.exercise = exercise;
        this.seedTestsFiles = Map.copyOf(seedTestsFiles);
        this.adaptation = adaptation;
        this.stageCheckService = stageCheckService;
    }

    /** Verify-free constructor for unit tests of the file and shell tools alone. */
    SandboxAgentTools(InteractiveSandbox sandbox, String sessionId) {
        this(sandbox, sessionId, null, null, Map.of(), false, null);
    }

    InteractiveSandbox checkpointSandbox() {
        return sandbox;
    }

    String checkpointSessionId() {
        return sessionId;
    }

    CheckpointState checkpointState() {
        return new CheckpointState(bashSequence, sandboxSessionTerminated, currentStage, repairWritableRoots == null ? null : Set.copyOf(repairWritableRoots),
                seededStructuralTests, structuralOracleRefresh != null, submitVetoed, dirtySinceLastPassingCheck, cachedPassingCheckStage, cachedPassingCheck, lastTestsReport);
    }

    void restoreCheckpointState(CheckpointState state) {
        if (state.structuralOracleRefreshConfigured() && structuralOracleRefresh == null) {
            throw new IllegalStateException("The checkpoint requires a structural-oracle refresh callback, but the resumed run did not configure one.");
        }
        bashSequence = state.bashSequence();
        sandboxSessionTerminated = state.sandboxSessionTerminated();
        currentStage = state.currentStage();
        repairWritableRoots = state.repairWritableRoots() == null ? null : Set.copyOf(state.repairWritableRoots());
        seededStructuralTests = state.seededStructuralTests();
        submitVetoed = state.submitVetoed();
        dirtySinceLastPassingCheck = state.dirtySinceLastPassingCheck();
        cachedPassingCheckStage = state.cachedPassingCheckStage();
        cachedPassingCheck = state.cachedPassingCheck();
        lastTestsReport = state.lastTestsReport();
    }

    String readFile(String path) {
        return readFile(path, null, null);
    }

    /**
     * Reads a workspace file, optionally starting at a 1-indexed line and bounded to a line count. Output never exceeds {@link #READ_INLINE_MAX_CHARS}; when the file is longer,
     * the result ends with a footer naming the exact {@code offset} to continue from, so a large file is paged through this same tool instead of silently losing its middle to
     * the loop-level cap.
     *
     * @param path   the workspace-relative path to read
     * @param offset the 1-indexed line to start reading from; {@code null} starts at the top
     * @param limit  the maximum number of lines to return; {@code null} reads to the inline budget
     * @return the (possibly paged) file content, or an actionable error message if the path is invalid or unreadable
     */
    @Tool(name = "read_file", description = AgentToolDescriptions.READ_FILE)
    public String readFile(@ToolParam(description = AgentToolDescriptions.READ_FILE_PATH) String path,
            @ToolParam(required = false, description = AgentToolDescriptions.READ_FILE_OFFSET) Integer offset,
            @ToolParam(required = false, description = AgentToolDescriptions.READ_FILE_LIMIT) Integer limit) {
        String safe = SandboxPathPolicy.workspaceRelativePath(path);
        if (safe == null) {
            return SandboxPathPolicy.invalidPathError(path);
        }
        HyperionSecretMaterialPolicy.Assessment pathAssessment = SECRET_MATERIAL_POLICY.assess(safe, new byte[0], HyperionSecretMaterialPolicy.Origin.TOOL_OBSERVATION);
        if (!pathAssessment.isSafe()) {
            return SECRET_MATERIAL_POLICY.blockedObservation(pathAssessment);
        }
        SandboxExecResultDTO result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "cat", WORKSPACE + "/" + safe);
        if (!result.isSuccess()) {
            return screenObservation(safe, "ERROR: could not read '" + safe + "': " + result.combinedOutput());
        }
        // Screen the FULL content, not the returned page, so a secret can never escape by straddling a page boundary.
        String screened = screenObservation(safe, result.stdout());
        if (!screened.equals(result.stdout())) {
            return screened;
        }
        return pageFileContent(safe, result.stdout(), offset, limit);
    }

    /**
     * Finds exact text in a workspace file or directory without allowing shell expansion or changing checkpoint state.
     *
     * @param path  the workspace-relative file or directory path
     * @param query the single-line text fragment
     * @return numbered matching lines, or an actionable error
     */
    @Tool(name = "search", description = AgentToolDescriptions.SEARCH)
    public String search(@ToolParam(description = AgentToolDescriptions.SEARCH_PATH) String path, @ToolParam(description = AgentToolDescriptions.SEARCH_QUERY) String query) {
        boolean searchWorkspaceRoot = path == null || path.isBlank();
        String safe = searchWorkspaceRoot ? "" : SandboxPathPolicy.workspaceRelativePath(path);
        if (!searchWorkspaceRoot && safe == null) {
            return SandboxPathPolicy.invalidPathError(path);
        }
        if (query == null || query.isBlank() || query.contains("\n") || query.contains("\r")) {
            return "ERROR: query must be non-empty text from a single line.";
        }
        HyperionSecretMaterialPolicy.Assessment pathAssessment = SECRET_MATERIAL_POLICY.assess(safe, new byte[0], HyperionSecretMaterialPolicy.Origin.TOOL_OBSERVATION);
        if (!pathAssessment.isSafe()) {
            return SECRET_MATERIAL_POLICY.blockedObservation(pathAssessment);
        }
        String target = searchWorkspaceRoot ? WORKSPACE : WORKSPACE + "/" + safe;
        String label = searchWorkspaceRoot ? "the workspace root" : safe;
        SandboxExecResultDTO result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "grep", "-RInFH", "--exclude-dir=.git", "--exclude-dir=target",
                "--exclude-dir=build", "--exclude-dir=node_modules", "--", query, target);
        if (result.exitCode() == 1 && result.stdout().isBlank()) {
            return "No matches in " + label + ".";
        }
        if (!result.isSuccess()) {
            return screenObservation(safe, "ERROR: could not search '" + label + "': " + result.combinedOutput());
        }
        StringBuilder safeMatches = new StringBuilder();
        for (String line : result.stdout().replace(WORKSPACE + "/", "").lines().toList()) {
            int pathEnd = line.indexOf(':');
            if (pathEnd < 1 || !SECRET_MATERIAL_POLICY.assess(line.substring(0, pathEnd), new byte[0], HyperionSecretMaterialPolicy.Origin.TOOL_OBSERVATION).isSafe()) {
                continue;
            }
            if (!safeMatches.isEmpty()) {
                safeMatches.append('\n');
            }
            safeMatches.append(line);
        }
        if (safeMatches.isEmpty()) {
            return "No safe matches in " + label + ".";
        }
        String output = safeMatches.toString();
        String screened = screenObservation(safe, output);
        if (!screened.equals(output)) {
            return screened;
        }
        if (screened.length() <= READ_INLINE_MAX_CHARS) {
            return screened.stripTrailing();
        }
        int lastLine = screened.lastIndexOf('\n', READ_INLINE_MAX_CHARS);
        return screened.substring(0, Math.max(0, lastLine)).stripTrailing() + "\n[More matches omitted. Narrow the query or path.]";
    }

    /** Cuts only on line boundaries, so a page never hands the model half a line it would then have to guess the rest of. */
    private static String pageFileContent(String safe, String content, @Nullable Integer offset, @Nullable Integer limit) {
        String[] allLines = content.split("\n", -1);
        // A trailing newline produces one final empty element that is an artifact of the split, not a real line.
        int totalLines = allLines.length > 1 && allLines[allLines.length - 1].isEmpty() ? allLines.length - 1 : allLines.length;
        int startLine = offset == null ? 1 : offset;
        if (startLine < 1) {
            return "ERROR: offset must be a 1-indexed line number (got " + startLine + ").";
        }
        if (startLine > totalLines) {
            return "ERROR: offset " + startLine + " is beyond the end of '" + safe + "' (" + totalLines + " lines total).";
        }
        if (limit != null && limit < 1) {
            return "ERROR: limit must be a positive line count (got " + limit + ").";
        }
        int requestedEnd = limit == null ? totalLines : Math.min(totalLines, startLine + limit - 1);
        StringBuilder selected = new StringBuilder();
        int lastIncludedLine = startLine - 1;
        for (int line = startLine; line <= requestedEnd; line++) {
            String text = allLines[line - 1];
            int addition = (selected.isEmpty() ? 0 : 1) + text.length();
            if (selected.length() + addition > READ_INLINE_MAX_CHARS) {
                if (selected.isEmpty()) {
                    // The first requested line alone exceeds the budget: hand back a shell recipe rather than an empty result the model cannot act on.
                    return "[Line " + line + " of '" + safe + "' is " + text.length() + " characters, more than the " + READ_INLINE_MAX_CHARS
                            + " this tool returns. Read it in slices with bash: sed -n '" + line + "p' " + safe + " | cut -c1-" + READ_INLINE_MAX_CHARS + "]";
                }
                break;
            }
            if (!selected.isEmpty()) {
                selected.append('\n');
            }
            selected.append(text);
            lastIncludedLine = line;
        }
        if (startLine == 1 && lastIncludedLine >= totalLines) {
            return content;
        }
        if (lastIncludedLine >= totalLines) {
            return selected.toString();
        }
        return selected + "\n\n[Showing lines " + startLine + "-" + lastIncludedLine + " of " + totalLines + ". Call read_file with offset=" + (lastIncludedLine + 1)
                + " to continue.]";
    }

    /**
     * Creates or overwrites a workspace file.
     *
     * @param path    the workspace-relative path to write
     * @param content the complete new file content
     * @return a confirmation, or an actionable error message
     */
    @Tool(name = "write_file", description = AgentToolDescriptions.WRITE_FILE)
    public String writeFile(@ToolParam(description = AgentToolDescriptions.WRITE_FILE_PATH) String path,
            @ToolParam(description = AgentToolDescriptions.WRITE_FILE_CONTENT) String content) {
        String safe = SandboxPathPolicy.workspaceRelativePath(path);
        if (safe == null) {
            return SandboxPathPolicy.invalidPathError(path);
        }
        if (!SandboxPathPolicy.isWritableGenerationPath(safe)) {
            return "ERROR: write only SPEC.md, test-plan.json, problem-statement.md, or files inside solution/, template/, or tests/. Workspace build infrastructure is managed by Artemis.";
        }
        String stageRejection = stageWriteRejection(safe);
        if (stageRejection != null) {
            return stageRejection;
        }
        if (SandboxPathPolicy.isManagedBuildInfrastructurePath(safe)) {
            return SandboxPathPolicy.immutableHarnessError(safe);
        }
        String contractRejection = approvedContractWriteRejection(safe, content);
        if (contractRejection != null) {
            return contractRejection + " No file was written; the workspace is unchanged. Do not delete or retry this path.";
        }
        String packageRejection = javaPackageWriteRejection(safe, content);
        if (packageRejection != null) {
            return packageRejection + " No file was written; the workspace is unchanged.";
        }
        // base64-encode the content so arbitrary source (quotes, newlines) is written verbatim; the path is allowlisted above so it cannot break the shell.
        String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String target = WORKSPACE + "/" + safe;
        String script = "mkdir -p \"$(dirname '" + target + "')\" && echo '" + encoded + "' | base64 -d > '" + target + "'";
        SandboxExecResultDTO result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "sh", "-c", script);
        if (!result.isSuccess()) {
            return "ERROR: could not write '" + safe + "': " + result.combinedOutput();
        }
        markDirty();
        return "Wrote " + content.length() + " characters to " + safe;
    }

    /**
     * Replaces a unique snippet of text in a workspace file.
     *
     * @param path    the workspace-relative path to edit
     * @param oldText the exact text to replace; must occur exactly once
     * @param newText the replacement text
     * @return a confirmation, or an actionable error message if the match is missing or ambiguous
     */
    @Tool(name = "edit_file", description = AgentToolDescriptions.EDIT_FILE)
    public String editFile(@ToolParam(description = AgentToolDescriptions.EDIT_FILE_PATH) String path,
            @ToolParam(description = AgentToolDescriptions.EDIT_FILE_OLD_TEXT) String oldText, @ToolParam(description = AgentToolDescriptions.EDIT_FILE_NEW_TEXT) String newText) {
        String safe = SandboxPathPolicy.workspaceRelativePath(path);
        if (safe == null) {
            return SandboxPathPolicy.invalidPathError(path);
        }
        if (oldText.isEmpty()) {
            return "ERROR: oldText must not be empty.";
        }
        String stageRejection = stageWriteRejection(safe);
        if (stageRejection != null) {
            return stageRejection;
        }
        SandboxExecResultDTO read = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "cat", WORKSPACE + "/" + safe);
        if (!read.isSuccess()) {
            return "ERROR: could not read '" + safe + "' for editing: " + read.combinedOutput();
        }
        String current = read.stdout();
        EditOutcome outcome = applyUniqueReplacement(safe, current, oldText, newText);
        if (outcome.error() != null) {
            return outcome.error();
        }
        String writeResult = writeFile(safe, outcome.content());
        if (!writeResult.startsWith("Wrote ")) {
            return writeResult;
        }
        return "Replaced 1 occurrence in " + safe + ".";
    }

    /** Either the updated file content or an agent-actionable {@code ERROR:} message; exactly one side is set. */
    private record EditOutcome(@Nullable String content, @Nullable String error) {

        static EditOutcome updated(String content) {
            return new EditOutcome(content, null);
        }

        static EditOutcome failed(String error) {
            return new EditOutcome(null, error);
        }
    }

    /**
     * Tries the exact text first and only then a normalized match (see {@link #normalizeForTolerantMatch}), because a tolerant match must never win over a byte-exact one. A
     * normalized match is accepted only when it is unique, and only the lines it touches are rewritten from the normalized text; every other line keeps its original bytes.
     */
    private static EditOutcome applyUniqueReplacement(String safe, String current, String oldText, String newText) {
        int occurrences = countOccurrences(current, oldText);
        if (occurrences > 1) {
            return EditOutcome.failed("ERROR: the provided oldText occurs " + occurrences + " times in '" + safe + "'. Provide more surrounding context to make it unique.");
        }
        if (occurrences == 1) {
            int first = current.indexOf(oldText);
            return EditOutcome.updated(current.substring(0, first) + newText + current.substring(first + oldText.length()));
        }
        String normalizedCurrent = normalizeForTolerantMatch(current);
        String normalizedOld = normalizeForTolerantMatch(oldText);
        int normalizedOccurrences = normalizedOld.isEmpty() ? 0 : countOccurrences(normalizedCurrent, normalizedOld);
        if (normalizedOccurrences > 1) {
            return EditOutcome.failed("ERROR: the provided oldText occurs " + normalizedOccurrences + " times in '" + safe + "' (ignoring whitespace-only differences). "
                    + "Provide more surrounding context to make it unique.");
        }
        if (normalizedOccurrences == 0) {
            return EditOutcome.failed("ERROR: the provided oldText was not found in '" + safe + "'. It must match the file exactly, including whitespace and newlines. "
                    + "Read the file again to get the exact current text.");
        }
        return spliceNormalizedMatch(current, normalizedCurrent, normalizedCurrent.indexOf(normalizedOld), normalizedOld.length(), newText);
    }

    /**
     * Rebuilds the file after a tolerant match: lines outside the matched range keep their original bytes, and only the matched range is emitted from the normalized text. Bails
     * out rather than corrupt the file if normalization changed the line structure, which {@link #normalizeForTolerantMatch} guarantees it does not.
     */
    private static EditOutcome spliceNormalizedMatch(String current, String normalizedCurrent, int matchIndex, int matchLength, String newText) {
        String[] originalLines = current.split("\n", -1);
        String[] normalizedLines = normalizedCurrent.split("\n", -1);
        if (originalLines.length != normalizedLines.length) {
            return EditOutcome.failed("ERROR: the provided oldText was not found in the file. Read the file again to get the exact current text.");
        }
        int matchEnd = matchIndex + matchLength;
        int lineStartOffset = 0;
        int startLine = 0;
        while (lineStartOffset + normalizedLines[startLine].length() < matchIndex) {
            lineStartOffset += normalizedLines[startLine].length() + 1;
            startLine++;
        }
        int endLine = startLine;
        int endLineStartOffset = lineStartOffset;
        while (endLineStartOffset + normalizedLines[endLine].length() < matchEnd) {
            endLineStartOffset += normalizedLines[endLine].length() + 1;
            endLine++;
        }
        int lineEndOffset = endLineStartOffset + normalizedLines[endLine].length();
        String replacedBlock = normalizedCurrent.substring(lineStartOffset, matchIndex) + newText + normalizedCurrent.substring(matchEnd, lineEndOffset);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < startLine; i++) {
            result.append(originalLines[i]).append('\n');
        }
        result.append(replacedBlock);
        for (int i = endLine + 1; i < originalLines.length; i++) {
            result.append('\n').append(originalLines[i]);
        }
        return EditOutcome.updated(result.toString());
    }

    private static int countOccurrences(String content, String needle) {
        int count = 0;
        for (int index = content.indexOf(needle); index >= 0; index = content.indexOf(needle, index + 1)) {
            count++;
        }
        return count;
    }

    /**
     * Folds the mismatches a model introduces when re-typing code it read earlier — NFKC, trailing whitespace per line, smart quotes, Unicode dashes and spaces — so a near-miss
     * edit succeeds instead of bouncing a "not found" error back for a byte-identical retry. Must never add or remove a newline: {@link #spliceNormalizedMatch} relies on line
     * indices staying aligned with the original text.
     */
    static String normalizeForTolerantMatch(String text) {
        String folded = Normalizer.normalize(text, Normalizer.Form.NFKC).replaceAll("[\u2018\u2019\u201A\u201B]", "'").replaceAll("[\u201C\u201D\u201E\u201F]", "\"")
                .replaceAll("[\u2010\u2011\u2012\u2013\u2014\u2015\u2212]", "-").replaceAll("[\u00A0\u2002-\u200A\u202F\u205F\u3000]", " ");
        return Arrays.stream(folded.split("\n", -1)).map(line -> line.replaceAll("[ \t]+$", "")).collect(Collectors.joining("\n"));
    }

    /**
     * Deletes one generated file without exposing unrestricted filesystem deletion to the model.
     *
     * @param path the workspace-relative file path to delete
     * @return a confirmation, or an actionable error message
     */
    @Tool(name = "delete_file", description = AgentToolDescriptions.DELETE_FILE)
    public String deleteFile(@ToolParam(description = AgentToolDescriptions.DELETE_FILE_PATH) String path) {
        String safe = SandboxPathPolicy.workspaceRelativePath(path);
        if (safe == null) {
            return SandboxPathPolicy.invalidPathError(path);
        }
        if (!SandboxPathPolicy.isWritableGenerationPath(safe)) {
            return "ERROR: delete only SPEC.md, test-plan.json, problem-statement.md, or files inside solution/, template/, or tests/. Workspace build infrastructure is managed by Artemis.";
        }
        String stageRejection = stageWriteRejection(safe);
        if (stageRejection != null) {
            return stageRejection;
        }
        if (SandboxPathPolicy.isManagedBuildInfrastructurePath(safe)) {
            return SandboxPathPolicy.immutableHarnessError(safe);
        }
        if ("SPEC.md".equals(safe)) {
            String contractRejection = approvedContractWriteRejection(safe, "");
            if (contractRejection != null) {
                return contractRejection;
            }
        }
        SandboxExecResultDTO result = sandbox.exec(sessionId, GenerationWorkspaceService.SANDBOX_READ_TIMEOUT, "sh", "-c",
                "if [ -e \"$1\" ] || [ -L \"$1\" ]; then rm -- \"$1\" && printf DELETED; else printf ABSENT; fi", "sh", WORKSPACE + "/" + safe);
        if (!result.isSuccess()) {
            return "ERROR: could not delete '" + safe + "': " + result.combinedOutput();
        }
        if ("ABSENT".equals(result.stdout())) {
            return "No change: '" + safe + "' does not exist.";
        }
        if (!"DELETED".equals(result.stdout())) {
            return "ERROR: could not determine whether '" + safe + "' was deleted.";
        }
        markDirty();
        return "Deleted " + safe;
    }

    /**
     * Runs a shell command in the workspace root.
     *
     * @param command the shell command to run, as a single string
     * @return the exit status followed by the combined stdout/stderr
     */
    @Tool(name = "bash", description = AgentToolDescriptions.BASH)
    public String bash(@ToolParam(description = AgentToolDescriptions.BASH_COMMAND) String command) {
        if (command == null || command.isBlank()) {
            return "exit=64\nNo command provided. Put the shell command in the \"command\" field as a single string, e.g. {\"command\": \"ls -R\"}.";
        }
        // Rejected up front rather than run: the shell's failure on an argv array reads to the model like a problem with the command, so it retries the same array form.
        if (isRenderedArgvArray(command)) {
            return "exit=2\nThe command must be a single shell string, e.g. {\"command\":\"ls -R\"}. You sent a JSON array, which I cannot run. Re-send it as one string.";
        }
        // `apply_patch` is not installed, so the shell would exit 127 and leave the workspace unchanged while the model believes the edit landed. Reject it loudly instead.
        if (isApplyPatchInvocation(command)) {
            return "exit=2\napply_patch is NOT available. Use write_file (new file / full rewrite) or edit_file (exact unique snippet) instead.";
        }
        if (SandboxPathPolicy.mutatesManagedBuildInfrastructure(command)) {
            return "exit=2\nDo not modify tests-repository build/harness files such as tests/pom.xml. They are seeded by Artemis and graded verbatim; edit only test source files under tests/test/<package path>/ instead.";
        }
        // Even a read-only inspection invalidates the cached passing check, because a shell command can mutate the workspace outside the file tools' guardrails. The case the
        // cache exists for — a passing check immediately followed by the loop ending — has no bash call in between, so being conservative here costs nothing.
        markDirty();
        int sequence = bashSequence++;
        String logPath = SPILL_DIR + "/bash-" + sequence + ".log";
        String commandPath = SPILL_DIR + "/bash-" + sequence + ".sh";
        List<String> protectedPaths = protectedWriteBoundaryPaths();
        String snapshotPath = SPILL_DIR + "/stage-snapshot-" + sequence;
        // Every element of the wrapper defends the meta line, which carries the only authoritative exit code. The command travels base64-encoded into its own script file so
        // unbalanced quoting fails inside `sh "$CMD"` rather than taking the wrapper down with it; the subshell keeps an `exit` inside the command (verify.sh does this) from
        // aborting the wrapper before it reports; output is redirected rather than piped because POSIX sh has no PIPESTATUS; and `wc` runs through `tr -d` because some
        // implementations pad the count with spaces. `ulimit -f` caps the spill, `</dev/null` stops a stdin-reading command hanging to the timeout, and coreutils `timeout`
        // stops a slow command before the session-destroying exec timeout fires.
        String encodedCommand = Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8));
        String script = "LOG=" + logPath + "\n" + "CMD=" + commandPath + "\n" + "SNAP=" + snapshotPath + "\n" + "mkdir -p " + SPILL_DIR + "\n" + "printf '%s' '" + encodedCommand
                + "' | base64 -d > \"$CMD\"\n" + stageSnapshotSetup(protectedPaths) + "if [ \"$snapshot_ok\" -eq 1 ] && command -v timeout >/dev/null 2>&1; then\n"
                + "  ( ulimit -f " + SPILL_ULIMIT_BLOCKS + " 2>/dev/null; cd " + WORKSPACE + " && timeout " + BASH_SOFT_TIMEOUT_SECONDS
                + " sh \"$CMD\" ) </dev/null > \"$LOG\" 2>&1\n" + "  rc=$?\n" + "elif [ \"$snapshot_ok\" -eq 1 ]; then\n" + "  ( ulimit -f " + SPILL_ULIMIT_BLOCKS
                + " 2>/dev/null; cd " + WORKSPACE + " && sh \"$CMD\" ) </dev/null > \"$LOG\" 2>&1\n" + "  rc=$?\n" + "else\n"
                + "  printf 'ERROR: Artemis could not checkpoint the current stage boundary, so the shell command was not run. Use the structured file tools instead.\\n' > \"$LOG\"\n"
                + "  rc=2\n" + "fi\n" + stageSnapshotRestore(protectedPaths) + "bytes=$(wc -c < \"$LOG\" | tr -d ' \\t')\n" + "lines=$(wc -l < \"$LOG\" | tr -d ' \\t')\n"
                + "printf '__HYP_META__ rc=%s bytes=%s lines=%s\\n' \"$rc\" \"$bytes\" \"$lines\"\n" + "tail -c " + BASH_TAIL_BYTES + " \"$LOG\"\n";
        SandboxExecResultDTO result = sandbox.exec(sessionId, BASH_TIMEOUT, "sh", "-c", script);
        if (result.timedOut()) {
            sandboxSessionTerminated = true;
            throw new LocalCIException("Sandbox command timed out and the sandbox session was terminated");
        }
        String output = composeBashOutput(result, logPath);
        if (stageCheckService != null) {
            List<String> contractRejections = Stream
                    .of(stageCheckService.restoreApprovedSpecAfterCommand(sandbox, sessionId), stageCheckService.approvedOwnershipViolationAfterCommand(sandbox, sessionId))
                    .flatMap(Optional::stream).toList();
            if (!contractRejections.isEmpty()) {
                output = String.join("\n", contractRejections) + "\n" + output;
            }
        }
        return screenObservation("tool/bash", output);
    }

    boolean isSandboxSessionTerminated() {
        return sandboxSessionTerminated;
    }

    /**
     * Called by the orchestrator before each stage's bounded agent loop. Resetting the cache here is what stops a fresh stage, or a re-entry into the same stage after gate
     * feedback, from reusing a pass computed before this call.
     *
     * @param stage the stage the orchestrator is about to run
     */
    public void enterStage(GenerationStage stage) {
        this.repairWritableRoots = null;
        this.currentStage = stage;
        this.dirtySinceLastPassingCheck = true;
        this.cachedPassingCheckStage = null;
        this.cachedPassingCheck = null;
    }

    /**
     * Limits an unstaged semantic repair to the coherent artifact surface implicated by its review finding. A later mechanical-correction attempt is unrestricted because its
     * verifier report provides concrete cross-artifact evidence.
     *
     * @param writableRoots workspace-root artifacts such as {@code tests}, {@code test-plan.json}, or {@code problem-statement.md}
     */
    public void enterRepairScope(Set<String> writableRoots) {
        this.currentStage = null;
        this.repairWritableRoots = Set.copyOf(writableRoots);
    }

    public void exitRepairScope() {
        this.repairWritableRoots = null;
    }

    public Set<String> seededStructuralTestNames() {
        return seededStructuralTests.testNames();
    }

    public SeededStructuralTests seededStructuralTests() {
        return seededStructuralTests;
    }

    /**
     * The structural oracle is server-owned, never agent-authored, so it is re-materialized at every TESTS and full-verification boundary rather than read from the workspace.
     *
     * @param refresh re-materializes the oracle and yields its authoritative test names
     */
    public void configureStructuralOracleRefresh(Supplier<SeededStructuralTests> refresh) {
        structuralOracleRefresh = refresh;
    }

    /**
     * @return the oracle's authoritative test names, empty when no refresh is installed
     */
    public SeededStructuralTests refreshStructuralOracle() {
        Supplier<SeededStructuralTests> refresh = structuralOracleRefresh;
        if (refresh != null) {
            SeededStructuralTests result = refresh.get();
            seededStructuralTests = result == null ? SeededStructuralTests.EMPTY : result;
        }
        return seededStructuralTests;
    }

    /**
     * Stages are monotonic: a stage may correct its own executable artifact or an earlier executable dependency, but never pre-author a later one. Writing ahead would populate
     * the template and tests before their dedicated instructions and gates exist, so the later stage spends its turns undoing an artifact that should not have crossed the
     * boundary. Repair loops may rewrite executable artifacts; the approved specification stays read-only through {@link #approvedContractWriteRejection}.
     */
    private @Nullable String stageWriteRejection(String path) {
        GenerationStage stage = currentStage;
        if (stage == null) {
            Set<String> writableRoots = repairWritableRoots;
            if (writableRoots == null || writableRoots.stream().anyMatch(root -> path.equals(root) || path.startsWith(root + "/"))) {
                return null;
            }
            return "ERROR: the current repair cannot write '" + path
                    + "'. Fix only the artifact surface implicated by the reviewed finding; verification will identify any concrete cross-artifact correction that is also needed.";
        }
        boolean allowed = switch (stage) {
            case SPEC -> path.equals("SPEC.md");
            case TESTS -> path.startsWith("solution/") || path.startsWith("template/") || path.startsWith("tests/") || path.equals("test-plan.json");
            case STATEMENT -> path.equals("problem-statement.md");
        };
        if (allowed) {
            return null;
        }
        return "ERROR: the current " + stage + " stage cannot write '" + path
                + "'. Finish only this stage's artifact (or correct an earlier dependency); the dedicated later stage will author this file with its own instructions and gate.";
    }

    /** The wrapper snapshots these and rolls them back by comparison, because guessing write intent from arbitrary shell command text is not something to attempt. */
    private static List<String> protectedStagePaths(@Nullable GenerationStage stage) {
        if (stage == null) {
            return List.of();
        }
        return switch (stage) {
            case SPEC -> List.of("solution", "template", "tests", "test-plan.json", "problem-statement.md");
            case TESTS -> List.of("SPEC.md", "problem-statement.md");
            case STATEMENT -> List.of("SPEC.md", "solution", "template", "tests", "test-plan.json");
        };
    }

    private List<String> protectedWriteBoundaryPaths() {
        if (currentStage != null) {
            return protectedStagePaths(currentStage);
        }
        Set<String> writableRoots = repairWritableRoots;
        if (writableRoots == null) {
            return List.of();
        }
        return List.of("SPEC.md", "solution", "template", "tests", "test-plan.json", "problem-statement.md").stream().filter(path -> !writableRoots.contains(path)).toList();
    }

    private static String stageSnapshotSetup(List<String> protectedPaths) {
        if (protectedPaths.isEmpty()) {
            return "snapshot_ok=1\n";
        }
        String paths = String.join(" ", protectedPaths);
        return "rm -rf \"$SNAP\"\n" + "mkdir -p \"$SNAP/data\"\n" + ": > \"$SNAP/present\"\n" + "snapshot_ok=1\n" + "for item in " + paths + "; do\n" + "  if [ -e \"" + WORKSPACE
                + "/$item\" ]; then\n" + "    if cp -a \"" + WORKSPACE + "/$item\" \"$SNAP/data/$item\"; then\n" + "      printf '%s\\n' \"$item\" >> \"$SNAP/present\"\n"
                + "    else\n" + "      snapshot_ok=0\n" + "    fi\n" + "  fi\n" + "done\n";
    }

    private static String stageSnapshotRestore(List<String> protectedPaths) {
        if (protectedPaths.isEmpty()) {
            return "";
        }
        String paths = String.join(" ", protectedPaths);
        return "if [ \"$snapshot_ok\" -eq 1 ]; then\n" + "  protected_changed=0\n" + "  for item in " + paths + "; do\n" + "    if grep -Fxq \"$item\" \"$SNAP/present\"; then\n"
                + "      if [ ! -e \"" + WORKSPACE + "/$item\" ] || ! diff -qr \"$SNAP/data/$item\" \"" + WORKSPACE + "/$item\" >/dev/null 2>&1; then protected_changed=1; fi\n"
                + "    elif [ -e \"" + WORKSPACE + "/$item\" ]; then\n" + "      protected_changed=1\n" + "    fi\n" + "  done\n" + "  if [ \"$protected_changed\" -eq 1 ]; then\n"
                + "    for item in " + paths + "; do\n" + "      rm -rf \"" + WORKSPACE + "/$item\"\n" + "      if grep -Fxq \"$item\" \"$SNAP/present\"; then\n"
                + "        cp -a \"$SNAP/data/$item\" \"" + WORKSPACE + "/$item\"\n" + "      fi\n" + "    done\n"
                + "    printf '\\nERROR: the shell command changed files outside the current stage. Artemis restored those protected artifacts; use the structured file tools only within this stage write boundary.\\n' >> \"$LOG\"\n"
                + "    rc=2\n" + "  fi\n" + "fi\n" + "rm -rf \"$SNAP\"\n";
    }

    private @Nullable String approvedContractWriteRejection(String path, String content) {
        return stageCheckService == null ? null : stageCheckService.validateArtifactWrite(sessionId, path, content).orElse(null);
    }

    /**
     * Rejects a Java source before it can enter a package that the grader cannot compile. This is build-contract feedback, not a semantic policy: the exact package comes from
     * the exercise configuration and the same source layout is shown to the model. Returning it on the write that introduced the mismatch avoids hiding a deterministic error
     * until the end-of-stage build.
     */
    private @Nullable String javaPackageWriteRejection(String path, String content) {
        if (exercise == null || exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA || exercise.getPackageName() == null || exercise.getPackageName().isBlank()
                || !path.endsWith(".java")) {
            return null;
        }
        String reason = ExerciseIntegrityGate.javaGeneratedSourceWriteReason(exercise.getPackageName().strip(), path, content);
        return reason == null ? null : "ERROR: Java build contract mismatch for '" + path + "'. " + reason;
    }

    /**
     * Called once after the staged run finishes, however it finished. The tools instance is shared with any later single-loop repair attempt, so without this reset
     * {@code verify} and {@code submit} would keep routing through whichever stage the staged run last entered. Idempotent even when no stage was ever entered.
     */
    public void exitStagedGeneration() {
        this.currentStage = null;
        this.repairWritableRoots = null;
        this.dirtySinceLastPassingCheck = true;
        this.cachedPassingCheckStage = null;
        this.cachedPassingCheck = null;
    }

    /**
     * Consulted only by the orchestrator's per-stage exit gate. {@code verify} and {@code submit} deliberately never read it and always run the live check.
     *
     * @param stage the stage the orchestrator is about to gate
     * @return the cached passing result if the stage matches and nothing has mutated the workspace since, otherwise empty
     */
    public Optional<StageCheckResult> reuseCachedPassingCheck(GenerationStage stage) {
        if (!dirtySinceLastPassingCheck && stage.equals(cachedPassingCheckStage) && cachedPassingCheck != null) {
            return Optional.of(cachedPassingCheck);
        }
        return Optional.empty();
    }

    /**
     * Called by the orchestrator right after its TESTS gate resolves. Necessary because that gate can resolve without any in-loop {@code verify} or {@code submit} call — a
     * reused cache, or a TESTS turn that called neither tool — leaving the STATEMENT stage's binding check with no exact test names to resolve against.
     *
     * @param report the TESTS stage's report, or {@code null} if none is available
     */
    public void recordLastTestsReport(@Nullable AgentVerifyReport report) {
        this.lastTestsReport = report;
    }

    private void markDirty() {
        this.dirtySinceLastPassingCheck = true;
    }

    private String composeBashOutput(SandboxExecResultDTO result, String logPath) {
        String output = result.combinedOutput() == null ? "" : result.combinedOutput();
        int newline = output.indexOf('\n');
        Matcher meta = BASH_META.matcher(newline < 0 ? output.strip() : output.substring(0, newline).strip());
        if (!meta.matches()) {
            // Meta line missing only if the wrapper itself failed; still return something actionable.
            return "exit=" + result.exitCode() + "\n" + charTail(output);
        }
        String rc = meta.group(1);
        long bytes = Long.parseLong(meta.group(2));
        String lines = meta.group(3);
        String body = newline < 0 ? "" : output.substring(newline + 1);
        // coreutils `timeout` reports a stopped command as 124, or 137 when it escalated to KILL. Naming the cause stops the model reading the partial output as a real result.
        String timeoutNote = "124".equals(rc) || "137".equals(rc) ? "\n\n[Exit code " + rc + " usually means the command was stopped at the " + BASH_SOFT_TIMEOUT_SECONDS
                + "-second limit. The output above is partial. " + "Run something faster or split the work.]" : "";
        if (bytes <= BASH_TAIL_BYTES) {
            // An explicit marker, so the model never has to guess whether a silent command succeeded quietly or its output was lost.
            return "exit=" + rc + "\n" + (body.isBlank() ? "(no output)" : body) + timeoutNote;
        }
        return "exit=" + rc + "\n" + body + "\n\n[Showing the last " + BASH_TAIL_BYTES + " of " + bytes + " bytes (" + lines + " lines total). Full output saved in the sandbox at "
                + logPath + " — read more with: tail -n 200 " + logPath + "  (or sed -n '1,200p' " + logPath + ", grep PATTERN " + logPath + ")]" + timeoutNote;
    }

    private static String charTail(String output) {
        if (output.length() <= BASH_TAIL_BYTES) {
            return output;
        }
        return "[showing the last " + BASH_TAIL_BYTES + " of " + output.length() + " characters]\n" + output.substring(output.length() - BASH_TAIL_BYTES);
    }

    /**
     * Runs the mechanical precheck for the current session: a staged session checks only the current stage, at the depth that stage can afford, and defers a check whose
     * artifacts do not exist yet rather than failing it; an unstaged session runs the full differential over the complete candidate.
     * <p>
     * Every call re-runs its check. A passing call populates the cache the orchestrator's exit gate may reuse, but never reads that cache itself.
     *
     * @return the agent-readable observation carrying a {@code MECHANICAL PRECHECK: PASS/FAIL} verdict line, or an error message if neither path is wired
     */
    @Tool(name = "verify", description = AgentToolDescriptions.VERIFY)
    public String verify() {
        GenerationStage stage = currentStage;
        if (stage != null) {
            return screenObservation("tool/verify", formatStageCheckObservation(runStageCheck(stage)));
        }
        if (verifier == null || exercise == null) {
            return "ERROR: the verify tool is unavailable in this session. Fall back to `sh verify.sh solution` and `sh verify.sh template` via bash.";
        }
        AgentVerifyReport report = verifier.selfCheck(sandbox, sessionId, exercise, seedTestsFiles, adaptation, refreshStructuralOracle());
        return screenObservation("tool/verify", report.toObservation());
    }

    private static String screenObservation(String logicalPath, String observation) {
        HyperionSecretMaterialPolicy.Assessment assessment = SECRET_MATERIAL_POLICY.assess(logicalPath, observation.getBytes(StandardCharsets.UTF_8),
                HyperionSecretMaterialPolicy.Origin.TOOL_OBSERVATION);
        return assessment.isSafe() ? observation : SECRET_MATERIAL_POLICY.blockedObservation(assessment);
    }

    /**
     * Signals that the exercise, or in a staged session this stage's artifact, is complete.
     * <p>
     * A staged {@code submit} re-runs the current stage's check itself rather than trusting an earlier {@code verify} call, however recent, and on failure vetoes the loop's
     * normal "submit ends the session" effect (see {@link SubmitVetoAware}) so the model must fix the reported issues and resubmit. An unstaged session is never gated.
     *
     * @param summary an optional one-line summary of what was created or changed
     * @return a confirmation, or a rejection carrying the stage check's report
     */
    @Tool(name = "submit", description = AgentToolDescriptions.SUBMIT)
    public String submit(@ToolParam(required = false, description = AgentToolDescriptions.SUBMIT_SUMMARY) String summary) {
        GenerationStage stage = currentStage;
        if (stage != null) {
            StageCheckResult result = runStageCheck(stage);
            // Assigned rather than only set on rejection, so a passing resubmit can never be blocked by a veto a caller failed to consume after an earlier rejection.
            submitVetoed = !result.passed();
            if (submitVetoed) {
                return screenObservation("tool/submit", "SUBMIT REJECTED — fix these and resubmit:\n" + formatStageCheckObservation(result));
            }
        }
        return "Submitted for verification" + (summary == null || summary.isBlank() ? "." : ": " + summary);
    }

    @Override
    public boolean consumeSubmitVeto() {
        boolean vetoed = submitVetoed;
        submitVetoed = false;
        return vetoed;
    }

    /** Shared by {@link #verify} and the gating half of {@link #submit} so their dispatch, caching, and report-threading cannot diverge. */
    private StageCheckResult runStageCheck(GenerationStage stage) {
        if (stageCheckService == null || exercise == null) {
            return StageCheckResult.failed("ERROR: the stage-check service is unavailable in this session.");
        }
        if (stage == GenerationStage.TESTS) {
            refreshStructuralOracle();
        }
        StageCheckResult result = stageCheckService.check(stage, sandbox, sessionId, exercise, seedTestsFiles, lastTestsReport, seededStructuralTests);
        if (stage == GenerationStage.TESTS && result.report() != null) {
            lastTestsReport = result.report();
        }
        if (result.passed()) {
            dirtySinceLastPassingCheck = false;
            cachedPassingCheckStage = stage;
            cachedPassingCheck = result;
        }
        return result;
    }

    /** Synthesizes the verdict line for observations that do not already state one, so {@code verify} and {@code submit} speak one vocabulary across every stage. */
    private static String formatStageCheckObservation(StageCheckResult result) {
        String observation = result.observation();
        if (observation != null && observation.contains(SELF_REPORTED_VERDICT_MARKER)) {
            return observation;
        }
        String verdict = result.passed() ? "MECHANICAL PRECHECK: PASS" : "MECHANICAL PRECHECK: FAIL";
        return observation == null || observation.isBlank() ? verdict : verdict + "\n" + observation;
    }

    /**
     * Matches the first shell word rather than a substring, so a command that merely mentions the name ({@code grep apply_patch}) still runs. A path prefix and a trailing
     * heredoc are tolerated.
     */
    static boolean isApplyPatchInvocation(String command) {
        String trimmed = command.strip();
        int firstWordEnd = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '<' || c == '\r') {
                firstWordEnd = i;
                break;
            }
        }
        String firstWord = trimmed.substring(0, firstWordEnd);
        int lastSlash = firstWord.lastIndexOf('/');
        String program = lastSlash >= 0 ? firstWord.substring(lastSlash + 1) : firstWord;
        return "apply_patch".equals(program);
    }

    static boolean isRenderedArgvArray(String command) {
        return RENDERED_ARGV_ARRAY.matcher(command.strip()).matches();
    }

    static boolean mutatesManagedBuildInfrastructure(String command) {
        return SandboxPathPolicy.mutatesManagedBuildInfrastructure(command);
    }

    /**
     * The allowlist is what makes every path below safe to interpolate into a shell command: it admits only source-file characters, so quotes, {@code $}, {@code ;} and
     * traversal are all rejected here rather than escaped later.
     *
     * @return the relative path, or {@code null} if it is unsafe
     */
    static String workspaceRelativePath(String path) {
        return SandboxPathPolicy.workspaceRelativePath(path);
    }
}
