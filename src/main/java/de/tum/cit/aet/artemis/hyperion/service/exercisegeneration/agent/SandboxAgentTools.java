package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ExerciseIntegrityGate;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * The file, shell, and verification tools the exercise-generation agent calls, bound to one sandbox session. Created per session (holds the session id), so not a Spring bean.
 * <p>
 * The agent has a full shell safely because correctness is never judged from what these tools report. In a staged session, {@code verify} and the gating half of {@code submit}
 * delegate to {@link StageCheckService} for the current stage's mechanical check (as cheap as that stage allows — a structure scan, one build, or the full differential); in an
 * unstaged (legacy) session {@code verify} always runs the same differential as the authoritative post-loop verifier. Either way this is advisory only; the post-loop verifier
 * decides mechanical validity.
 */
public class SandboxAgentTools implements SubmitVetoAware {

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    private static final String WORKSPACE = "/workspace";

    private static final Duration FILE_OP_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration BASH_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Soft per-command time limit enforced INSIDE the wrapper (via coreutils {@code timeout}, when the image has it), deliberately below {@link #BASH_TIMEOUT}: the docker-exec
     * timeout has no clean way to kill just the command, so hitting it destroys the whole sandbox session and the run dies. With the soft limit, a slow command is stopped in
     * the container, its partial output survives in the spill log, the model is told what happened, and the session continues — the exec timeout remains only as a backstop for
     * images without {@code timeout}.
     */
    private static final int BASH_SOFT_TIMEOUT_SECONDS = 270;

    /** Spill directory inside the sandbox, outside /workspace so it is never picked up by repository extraction. */
    private static final String SPILL_DIR = "/tmp/hyperion";

    /**
     * Bytes of the output tail returned inline. Kept under the downstream per-tool-result cap (AgentLoopRunner.MAX_TOOL_RESPONSE_CHARS = 12000) so the result is never re-truncated
     * there and the truncation marker the agent sees stays truthful; the full output lives in the spill file.
     */
    private static final int BASH_TAIL_BYTES = 10_000;

    /**
     * Characters of file content {@code read_file} returns inline per call. Same rationale as {@link #BASH_TAIL_BYTES}: staying under the downstream per-tool-result cap keeps the
     * continuation footer truthful — the loop-level middle-elision never fires on top of it, and the footer's {@code offset} is always the real next line to request.
     */
    static final int READ_INLINE_MAX_CHARS = 10_000;

    /** Per-command spill-file ceiling via {@code ulimit -f} (512-byte blocks): 65536 * 512 = 32 MB, so a runaway command cannot fill the container disk before the timeout. */
    private static final int SPILL_ULIMIT_BLOCKS = 65_536;

    /** First line the bash wrapper prints, carrying the real exit code and size (the container exec's own exit code reflects the wrapper, not the command). */
    private static final Pattern BASH_META = Pattern.compile("^__HYP_META__ rc=(-?\\d+) bytes=(\\d+) lines=(\\d+)$");

    /**
     * Matches the {@code List.toString()} render of a JSON argv array (e.g. {@code [bash, -lc, ls -R]}) Spring AI produces from {@code {"command":[...]}}. A POSIX {@code [ -f x ]}
     * test has a space after the bracket so it does not match; a single-element {@code [foo]} has no comma so it does not either.
     */
    private static final Pattern MANGLED_ARRAY = Pattern.compile("^\\[\\S.*,.*]$", Pattern.DOTALL);

    /**
     * Marker line an observation carries when it already states its own pass/fail verdict (the TESTS stage's {@link AgentVerifyReport#toObservation()}), so the generic
     * "MECHANICAL PRECHECK: PASS/FAIL" header {@link #formatStageCheckObservation} synthesizes for the other stages is not doubled up on top of it.
     */
    private static final String VERDICT_LINE_MARKER = "MECHANICAL PRECHECK:";

    private final InteractiveSandbox sandbox;

    private final String sessionId;

    /**
     * The authoritative verifier, reused by the {@code verify} tool to run the same differential as the post-loop mechanical gate in an unstaged session; {@code null} disables
     * the legacy fallback in tests.
     */
    @Nullable
    private final DifferentialVerificationService verifier;

    /** The exercise whose per-language {@code verify.sh} and SCA configuration the {@code verify} tool's differential uses; {@code null} disables the tool in tests. */
    @Nullable
    private final ProgrammingExercise exercise;

    /**
     * The per-stage mechanical gate, consulted by {@code verify} and the gating half of {@code submit} in a staged session; {@code null} in an unstaged session or a test that
     * does not need staged dispatch.
     */
    @Nullable
    private final StageCheckService stageCheckService;

    /** Seeded test files let the in-loop verifier distinguish untouched legacy tests from files authored or changed in this run. */
    private final Map<String, String> seedTestsFiles;

    private final boolean adaptation;

    /** Per-command spill-file counter; unsynchronized is safe — the agent loop calls the tools serially within a session and each session has its own instance and container. */
    private int bashSequence = 0;

    private boolean sandboxSessionTerminated;

    /**
     * The stage this session is currently running, set by the orchestrator via {@link #enterStage} before it starts each stage's bounded agent loop. {@code null} (the default)
     * means the session is unstaged — the legacy single-loop behavior, where every tool behaves exactly as it always has. Volatile because the orchestrator and the agent loop
     * are not guaranteed to run on the same thread across a stage transition, even though a single stage's tool calls are always serial.
     */
    private volatile GenerationStage currentStage;

    /**
     * Set by {@link #submit} when the current stage's mechanical check rejected the submission, so {@link AgentLoopRunner} keeps the loop going instead of ending the session;
     * cleared by {@link #consumeSubmitVeto()}.
     */
    private volatile boolean submitVetoed;

    /**
     * Whether a file/shell tool has run since the last PASSING stage check, so a later check for the same stage cannot be skipped. Starts {@code true} (no passing check has
     * run yet) and is reset by {@link #enterStage}.
     */
    private volatile boolean dirtySinceLastPassingCheck = true;

    /**
     * The stage {@link #cachedPassingCheck} applies to; consulted alongside {@link #dirtySinceLastPassingCheck} by {@link #reuseCachedPassingCheck} so a cache from an earlier
     * stage can never be misread as current.
     */
    @Nullable
    private volatile GenerationStage cachedStage;

    /** The last PASSING stage check's result, reused by the orchestrator's exit gate when nothing has changed since (see {@link #reuseCachedPassingCheck}). */
    @Nullable
    private volatile StageCheckResult cachedPassingCheck;

    /**
     * The TESTS stage's most recent {@link AgentVerifyReport}, so the STATEMENT stage's binding check can resolve {@code [task]} names without its own build. Set both
     * opportunistically by an in-loop TESTS-stage check and authoritatively by {@link #recordLastTestsReport} after the orchestrator's own TESTS gate resolves.
     */
    @Nullable
    private volatile AgentVerifyReport lastTestsReport;

    /**
     * @param sandbox   the sandbox session the tools operate on
     * @param sessionId the session handle
     * @param verifier  the authoritative verifier the {@code verify} tool reuses for the in-loop self-check in an unstaged session
     * @param exercise  the exercise whose {@code verify.sh}/SCA config the {@code verify} tool's differential uses
     */
    public SandboxAgentTools(InteractiveSandbox sandbox, String sessionId, DifferentialVerificationService verifier, ProgrammingExercise exercise) {
        this(sandbox, sessionId, verifier, exercise, Map.of(), false, null);
    }

    /**
     * @param sandbox           the sandbox session the tools operate on
     * @param sessionId         the session handle
     * @param verifier          the authoritative verifier the {@code verify} tool reuses for the in-loop self-check in an unstaged session
     * @param exercise          the exercise whose {@code verify.sh}/SCA config the {@code verify} tool's differential uses
     * @param seedTestsFiles    the tests-repository snapshot taken before generation
     * @param adaptation        whether this run adapts an existing exercise
     * @param stageCheckService the per-stage mechanical gate {@code verify}/{@code submit} delegate to once {@link #enterStage} has been called; {@code null} while a stage is
     *                              active makes both tools report the stage-check service as unavailable rather than silently falling back to the unstaged differential
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

    /**
     * Verify-free constructor: the verifier, exercise, and stage-check service are absent, so neither {@code verify} nor a gated {@code submit} is wired. Used by unit tests of
     * the file/shell tools.
     *
     * @param sandbox   the sandbox session the tools operate on
     * @param sessionId the session handle
     */
    SandboxAgentTools(InteractiveSandbox sandbox, String sessionId) {
        this(sandbox, sessionId, null, null, Map.of(), false, null);
    }

    /** Convenience for callers and tests that want the default whole-file read (subject to the same inline budget as the tool call). */
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
        String safe = workspaceRelativePath(path);
        if (safe == null) {
            return invalidPathError(path);
        }
        HyperionSecretMaterialPolicy.Assessment pathAssessment = SECRET_MATERIAL_POLICY.assess(safe, new byte[0], HyperionSecretMaterialPolicy.Origin.TOOL_OBSERVATION);
        if (!pathAssessment.isSafe()) {
            return SECRET_MATERIAL_POLICY.blockedObservation(pathAssessment);
        }
        SandboxExecResult result = sandbox.exec(sessionId, FILE_OP_TIMEOUT, "cat", WORKSPACE + "/" + safe);
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
     * Applies offset/limit and the inline character budget to file content, cutting only on line boundaries and appending a continuation footer that names the exact next
     * {@code offset} whenever anything was left out.
     */
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
                    // The first requested line alone exceeds the budget; hand the model a shell recipe instead of returning nothing.
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
            // An offset read that reached the end of the file: return the slice as-is, nothing was left out.
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
        String safe = workspaceRelativePath(path);
        if (safe == null) {
            return invalidPathError(path);
        }
        if (!isWritableGenerationPath(safe)) {
            return "ERROR: write only SPEC.md, DESIGN.md, problem-statement.md, or files inside solution/, template/, or tests/. Workspace build infrastructure is managed by Artemis.";
        }
        if (isManagedBuildInfrastructurePath(safe)) {
            return immutableHarnessError(safe);
        }
        // base64-encode the content so arbitrary source (quotes, newlines) is written verbatim; the path is allowlisted above so it cannot break the shell.
        String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String target = WORKSPACE + "/" + safe;
        String script = "mkdir -p \"$(dirname '" + target + "')\" && echo '" + encoded + "' | base64 -d > '" + target + "'";
        SandboxExecResult result = sandbox.exec(sessionId, FILE_OP_TIMEOUT, "sh", "-c", script);
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
        String safe = workspaceRelativePath(path);
        if (safe == null) {
            return invalidPathError(path);
        }
        if (oldText.isEmpty()) {
            return "ERROR: oldText must not be empty.";
        }
        SandboxExecResult read = sandbox.exec(sessionId, FILE_OP_TIMEOUT, "cat", WORKSPACE + "/" + safe);
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
     * Finds {@code oldText} in {@code current} and returns the updated content, or an {@code ERROR:} message the model can act on. Tries the exact text first; when that finds
     * nothing, retries in a normalized space that forgives the mismatches models actually produce when re-typing code they read earlier — trailing whitespace, smart quotes,
     * Unicode dashes and non-breaking spaces (see {@link #normalizeForTolerantMatch}). A normalized match is only accepted when it is unique, and only the lines it touches are
     * rewritten from the normalized text; every other line keeps its original bytes.
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
     * Rebuilds the file after a tolerant (normalized-space) match: original lines outside the matched line range are kept byte-for-byte; the matched range is emitted from the
     * normalized text with {@code newText} substituted. Fails loud (never expected — {@link #normalizeForTolerantMatch} preserves newlines) rather than corrupt the file if the
     * normalization changed the line structure.
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
     * Normalizes text for the tolerant second-chance match in {@link #applyUniqueReplacement}: NFKC, trailing whitespace stripped per line, smart quotes and Unicode
     * dashes/spaces folded to their ASCII forms. Never adds or removes newlines, so line indices stay aligned with the original text. These are exactly the mismatches models
     * introduce when re-typing code they read earlier, so absorbing them fixes the edit at the root instead of bouncing a "not found" error back for a byte-identical retry.
     */
    static String normalizeForTolerantMatch(String text) {
        String folded = Normalizer.normalize(text, Normalizer.Form.NFKC)
                // Smart single quotes, smart double quotes, Unicode hyphens/dashes/minus, non-breaking and typographic spaces: each folded to its ASCII form.
                .replaceAll("[\u2018\u2019\u201A\u201B]", "'").replaceAll("[\u201C\u201D\u201E\u201F]", "\"").replaceAll("[\u2010\u2011\u2012\u2013\u2014\u2015\u2212]", "-")
                .replaceAll("[\u00A0\u2002-\u200A\u202F\u205F\u3000]", " ");
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
        String safe = workspaceRelativePath(path);
        if (safe == null) {
            return invalidPathError(path);
        }
        if (!isWritableGenerationPath(safe)) {
            return "ERROR: delete only SPEC.md, DESIGN.md, problem-statement.md, or files inside solution/, template/, or tests/. Workspace build infrastructure is managed by Artemis.";
        }
        if (isManagedBuildInfrastructurePath(safe)) {
            return immutableHarnessError(safe);
        }
        SandboxExecResult result = sandbox.exec(sessionId, FILE_OP_TIMEOUT, "rm", "-f", "--", WORKSPACE + "/" + safe);
        if (!result.isSuccess()) {
            return "ERROR: could not delete '" + safe + "': " + result.combinedOutput();
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
        // Reject the mangled argv-array form (Spring AI's List.toString() of {"command":[...]}) up front: it is not runnable, and the model misreads the resulting failure and
        // retries the array. A genuine POSIX test starts with "[ " so it never matches.
        if (isMangledArrayCommand(command)) {
            return "exit=2\nThe command must be a single shell string, e.g. {\"command\":\"ls -R\"}. You sent a JSON array, which I cannot run. Re-send it as one string.";
        }
        // Short-circuit a Codex-style `apply_patch` invocation: it is not installed, so the shell would exit 127 but leave the workspace unchanged while the model believes the
        // edit
        // landed and thrashes. Reject loudly without touching the sandbox so the agent switches to write_file / edit_file.
        if (isApplyPatchInvocation(command)) {
            return "exit=2\napply_patch is NOT available. Use write_file (new file / full rewrite) or edit_file (exact unique snippet) instead.";
        }
        if (mutatesManagedBuildInfrastructure(command)) {
            return "exit=2\nDo not modify tests-repository build/harness files such as tests/pom.xml. They are seeded by Artemis and graded verbatim; edit only test source files under tests/test/<package path>/ instead.";
        }
        // A shell command can mutate the workspace outside the write_file/edit_file/delete_file guardrails, so it always invalidates a cached passing stage check even though most
        // bash calls are read-only inspection. Conservative by design: the one case the clean-skip exists for — a passing verify/submit immediately followed by the loop ending —
        // never has a bash call in between, so this never costs that path anything.
        markDirty();
        int sequence = bashSequence++;
        String logPath = SPILL_DIR + "/bash-" + sequence + ".log";
        String commandPath = SPILL_DIR + "/bash-" + sequence + ".sh";
        // The command travels base64-encoded into its own script file, so its quoting can never corrupt the wrapper: a command with unbalanced quotes now fails INSIDE
        // `sh "$CMD"` with an ordinary error and exit code instead of taking the meta line down with it. The wrapper runs it in a subshell so an `exit` inside (e.g. from
        // verify.sh) cannot abort the wrapper before it reports the code and tail; combined output is redirected, not piped (POSIX sh has no PIPESTATUS), so the real exit code
        // comes from `$?`; `ulimit -f` caps the spill size, `</dev/null` stops a stdin-reading command from hanging until the timeout, and coreutils `timeout` (when the image
        // has it) stops a slow command before the session-destroying docker-exec timeout can fire. `wc` is run through `tr -d` because some implementations pad the count with
        // spaces, which would corrupt the meta line and lose the authoritative exit code.
        String encodedCommand = Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8));
        String script = "LOG=" + logPath + "\n" + "CMD=" + commandPath + "\n" + "mkdir -p " + SPILL_DIR + "\n" + "printf '%s' '" + encodedCommand + "' | base64 -d > \"$CMD\"\n"
                + "if command -v timeout >/dev/null 2>&1; then\n" + "  ( ulimit -f " + SPILL_ULIMIT_BLOCKS + " 2>/dev/null; cd " + WORKSPACE + " && timeout "
                + BASH_SOFT_TIMEOUT_SECONDS + " sh \"$CMD\" ) </dev/null > \"$LOG\" 2>&1\n" + "else\n" + "  ( ulimit -f " + SPILL_ULIMIT_BLOCKS + " 2>/dev/null; cd " + WORKSPACE
                + " && sh \"$CMD\" ) </dev/null > \"$LOG\" 2>&1\n" + "fi\n" + "rc=$?\n" + "bytes=$(wc -c < \"$LOG\" | tr -d ' \\t')\n"
                + "lines=$(wc -l < \"$LOG\" | tr -d ' \\t')\n" + "printf '__HYP_META__ rc=%s bytes=%s lines=%s\\n' \"$rc\" \"$bytes\" \"$lines\"\n" + "tail -c " + BASH_TAIL_BYTES
                + " \"$LOG\"\n";
        SandboxExecResult result = sandbox.exec(sessionId, BASH_TIMEOUT, "sh", "-c", script);
        if (result.timedOut()) {
            sandboxSessionTerminated = true;
            throw new LocalCIException("Sandbox command timed out and the sandbox session was terminated");
        }
        return screenObservation("tool/bash", composeBashOutput(result, logPath));
    }

    boolean isSandboxSessionTerminated() {
        return sandboxSessionTerminated;
    }

    /**
     * Called by the orchestrator before starting a stage's bounded agent loop, so the tools that follow behave according to that stage's rules (currently: {@link #verify}'s and
     * {@link #submit}'s dispatch to {@link StageCheckService}). Resets the clean/dirty tracking to dirty and drops any cached passing check, so a fresh stage (or a re-entry into
     * the same stage after gate feedback) never reuses a pass computed before this call. Never called for an unstaged (legacy single-loop) session, which leaves
     * {@link #currentStage} {@code null}.
     *
     * @param stage the stage the orchestrator is about to run
     */
    public void enterStage(GenerationStage stage) {
        this.currentStage = stage;
        this.dirtySinceLastPassingCheck = true;
        this.cachedStage = null;
        this.cachedPassingCheck = null;
    }

    /**
     * Called once by the orchestrator after the staged run finishes (successfully, on a gate failure, or on the wall-clock ceiling), so a later legacy single-loop repair
     * attempt on this same shared tools instance (see the orchestrator's outer repair-attempt loop) is unstaged again instead of incorrectly keeping the last stage's dispatch —
     * without this, {@code verify}/{@code submit} on a repair attempt would still route through {@link StageCheckService} for whichever stage the staged run last entered.
     * Idempotent and safe to call even when no stage was ever entered.
     */
    public void exitStagedGeneration() {
        this.currentStage = null;
        this.dirtySinceLastPassingCheck = true;
        this.cachedStage = null;
        this.cachedPassingCheck = null;
    }

    /**
     * Consulted by the orchestrator's per-stage exit gate so a stage whose check already passed — and which has seen no write/edit/delete/bash call since — does not pay for a
     * redundant re-check; see {@link StagedGenerationRunner}. Not consulted by {@code verify}/{@code submit} themselves, which always run the live check (a passing check with no
     * edits afterwards makes only the {@code exit gate} instant, per the staged-workflow prompt).
     *
     * @param stage the stage the orchestrator is about to gate
     * @return the cached passing result if the stage matches and nothing has mutated the workspace since, otherwise empty
     */
    public Optional<StageCheckResult> reuseCachedPassingCheck(GenerationStage stage) {
        if (!dirtySinceLastPassingCheck && stage.equals(cachedStage) && cachedPassingCheck != null) {
            return Optional.of(cachedPassingCheck);
        }
        return Optional.empty();
    }

    /**
     * Records the TESTS stage's authoritative {@link AgentVerifyReport} (whether freshly computed or reused from the cache), called by the orchestrator right after its TESTS
     * gate resolves. Necessary because the orchestrator's own gate evaluation can bypass an in-loop {@code verify}/{@code submit} call entirely (a reused cache, or a TESTS-stage
     * agent turn that never called either tool), in which case this field would otherwise stay unset and the STATEMENT stage's binding check would have no exact test names to
     * resolve against.
     *
     * @param report the TESTS stage's report, or {@code null} if none is available
     */
    public void recordLastTestsReport(@Nullable AgentVerifyReport report) {
        this.lastTestsReport = report;
    }

    /** Marks the current stage dirty: a write_file/edit_file/delete_file/bash call happened, so any cached passing check for it is no longer trustworthy. */
    private void markDirty() {
        this.dirtySinceLastPassingCheck = true;
    }

    /**
     * Composes the model-facing result: parses the leading {@code __HYP_META__} line for the real exit code and size, then returns the exit code, the output tail, and — when the
     * output exceeded the tail — a marker pointing at the spill file.
     */
    private String composeBashOutput(SandboxExecResult result, String logPath) {
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
        // coreutils `timeout` reports a stopped command as 124 (137 when it had to escalate to KILL). Name the likely cause so the model shortens or splits the command
        // instead of misreading the partial output as the command's real result.
        String timeoutNote = "124".equals(rc) || "137".equals(rc) ? "\n\n[Exit code " + rc + " usually means the command was stopped at the " + BASH_SOFT_TIMEOUT_SECONDS
                + "-second limit. The output above is partial. " + "Run something faster or split the work.]" : "";
        if (bytes <= BASH_TAIL_BYTES) {
            // An explicit marker instead of dead air: the model should not have to guess whether a silent command succeeded quietly or the output was lost.
            return "exit=" + rc + "\n" + (body.isBlank() ? "(no output)" : body) + timeoutNote;
        }
        return "exit=" + rc + "\n" + body + "\n\n[Showing the last " + BASH_TAIL_BYTES + " of " + bytes + " bytes (" + lines + " lines total). Full output saved in the sandbox at "
                + logPath + " — read more with: tail -n 200 " + logPath + "  (or sed -n '1,200p' " + logPath + ", grep PATTERN " + logPath + ")]" + timeoutNote;
    }

    /** Last-resort character tail used only when the wrapper's meta line is unexpectedly absent. */
    private static String charTail(String output) {
        if (output.length() <= BASH_TAIL_BYTES) {
            return output;
        }
        return "[showing the last " + BASH_TAIL_BYTES + " of " + output.length() + " characters]\n" + output.substring(output.length() - BASH_TAIL_BYTES);
    }

    /**
     * Runs the mechanical precheck for the current session.
     * <p>
     * In a staged session ({@link #currentStage} set), {@code verify} delegates to {@link StageCheckService} for the CURRENT stage at that stage's right depth — a free structure
     * scan for {@link GenerationStage#DESIGN}, one pristine build for {@link GenerationStage#SOLUTION} and {@link GenerationStage#TEMPLATE}, the full solution/template
     * differential for {@link GenerationStage#TESTS} (identical to the unstaged path below), and a no-build binding check for {@link GenerationStage#STATEMENT} against the TESTS
     * stage's exact test names. Every call re-runs the check (no cache); a passing call clears {@link #dirtySinceLastPassingCheck} for the orchestrator's exit gate to reuse (see
     * {@link #reuseCachedPassingCheck}), but never skips itself. An unstaged session ({@code currentStage} {@code null}) keeps the legacy behavior: always the full differential.
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
        AgentVerifyReport report = verifier.selfCheck(sandbox, sessionId, exercise, seedTestsFiles, adaptation);
        return screenObservation("tool/verify", report.toObservation());
    }

    private static String screenObservation(String logicalPath, String observation) {
        HyperionSecretMaterialPolicy.Assessment assessment = SECRET_MATERIAL_POLICY.assess(logicalPath, observation.getBytes(StandardCharsets.UTF_8),
                HyperionSecretMaterialPolicy.Origin.TOOL_OBSERVATION);
        return assessment.isSafe() ? observation : SECRET_MATERIAL_POLICY.blockedObservation(assessment);
    }

    /**
     * Signals that the exercise (or, in a staged session, this stage's artifact) is complete.
     * <p>
     * In a staged session, {@code submit} first re-runs the current stage's mechanical check itself (never trusting an earlier {@code verify} call, however recent) and, on
     * failure, rejects the submission and vetoes the agent loop's normal "submit ends the session" effect (see {@link SubmitVetoAware}) so the loop keeps going instead of ending
     * — the model must fix the reported issues and call {@code submit} again. An unstaged session is never gated: {@code submit} always ends the loop, exactly as before this
     * seam existed.
     *
     * @param summary an optional one-line summary of what was created or changed
     * @return a confirmation that the work was submitted for verification, or (staged session, failing check) a rejection carrying the stage check's report
     */
    @Tool(name = "submit", description = AgentToolDescriptions.SUBMIT)
    public String submit(@ToolParam(required = false, description = AgentToolDescriptions.SUBMIT_SUMMARY) String summary) {
        GenerationStage stage = currentStage;
        if (stage != null) {
            StageCheckResult result = runStageCheck(stage);
            // Set unconditionally (not only on rejection): this call's outcome is authoritative for whether THIS submit is vetoed, so a passing resubmit can never be blocked by
            // a veto a caller failed to consume after an earlier rejection.
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

    /**
     * Runs the current stage's live mechanical check via {@link StageCheckService}, threading the TESTS stage's report into the STATEMENT stage's binding check and back out
     * again, and updates the clean/dirty cache on a pass. Shared by {@link #verify} and the gating half of {@link #submit} so their dispatch, caching, and report-threading can
     * never diverge.
     *
     * @param stage the current stage ({@link #currentStage}, guaranteed non-null by both callers)
     * @return the stage's check result; a synthetic failure naming the missing wiring if {@link #stageCheckService} or {@link #exercise} is absent
     */
    private StageCheckResult runStageCheck(GenerationStage stage) {
        if (stageCheckService == null || exercise == null) {
            return StageCheckResult.failed("ERROR: the stage-check service is unavailable in this session.");
        }
        StageCheckResult result = stageCheckService.check(stage, sandbox, sessionId, exercise, seedTestsFiles, lastTestsReport);
        if (stage == GenerationStage.TESTS && result.report() != null) {
            lastTestsReport = result.report();
        }
        if (result.passed()) {
            dirtySinceLastPassingCheck = false;
            cachedStage = stage;
            cachedPassingCheck = result;
        }
        return result;
    }

    /**
     * Renders a stage check as the agent-facing observation: the TESTS stage's observation already carries its own {@code MECHANICAL PRECHECK: PASS/FAIL} verdict line (see
     * {@link AgentVerifyReport#toObservation()}) and is returned verbatim; every other stage's observation gets that same verdict line synthesized on top, so {@code verify} and
     * {@code submit} speak one consistent vocabulary regardless of which stage produced the result.
     */
    private static String formatStageCheckObservation(StageCheckResult result) {
        String observation = result.observation();
        if (observation != null && observation.contains(VERDICT_LINE_MARKER)) {
            return observation;
        }
        String verdict = result.passed() ? "MECHANICAL PRECHECK: PASS" : "MECHANICAL PRECHECK: FAIL";
        return observation == null || observation.isBlank() ? verdict : verdict + "\n" + observation;
    }

    /**
     * Detects whether the command's first shell word is {@code apply_patch} (a path prefix like {@code ./} or a trailing heredoc {@code <<'PATCH'} is tolerated). Matching the
     * first
     * token, not a substring, avoids flagging a command that merely mentions it (e.g. {@code grep apply_patch}).
     *
     * @param command the effective shell command
     * @return {@code true} if the command would run {@code apply_patch}
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

    /**
     * Detects the mangled {@code List.toString()} form of a JSON argv array (e.g. {@code [bash, -lc, ls -R]}) Spring AI coerces from {@code {"command":[...]}}, which is not a
     * runnable shell command. A genuine POSIX {@code [ -f x ]} test has a space after the {@code [} so it does not match. See {@link #MANGLED_ARRAY}.
     *
     * @param command the effective shell command
     * @return {@code true} if the command is the rendered form of a JSON array rather than a real shell string
     */
    static boolean isMangledArrayCommand(String command) {
        return MANGLED_ARRAY.matcher(command.strip()).matches();
    }

    private static boolean isManagedBuildInfrastructurePath(String safe) {
        for (String repository : List.of("solution/", "template/", "tests/")) {
            if (safe.startsWith(repository)) {
                String repositoryPath = safe.substring(repository.length());
                return repositoryPath.startsWith("buildSrc/") || repositoryPath.startsWith("gradle/") || ExerciseIntegrityGate.isHarnessFile(repositoryPath);
            }
        }
        return false;
    }

    /**
     * Whether a file may be written or deleted through {@link #writeFile}/{@link #deleteFile}. {@code DESIGN.md} is the one workspace-root file allowed: it is the agent's
     * working-memory design note (see {@link GenerationStage#DESIGN}), never read by repository extraction, and legitimately updated from any stage — including legacy
     * (unstaged) sessions and every staged one — whenever a later stage forces a design change.
     */
    private static boolean isWritableGenerationPath(String safe) {
        return safe.equals("DESIGN.md") || safe.equals("SPEC.md") || safe.equals("problem-statement.md") || safe.startsWith("solution/") || safe.startsWith("template/")
                || safe.startsWith("tests/");
    }

    private static String immutableHarnessError(String safe) {
        return "ERROR: do not modify " + safe + ". Repository build infrastructure is seeded and managed by Artemis; edit only the problem statement and exercise source files.";
    }

    static boolean mutatesManagedBuildInfrastructure(String command) {
        String lower = command.toLowerCase();
        if (!lower.matches(
                "(?s).*(?:tests|solution|template)/(buildsrc/.*|gradle/.*|pom\\.xml|build\\.gradle|build\\.gradle\\.kts|settings\\.gradle|settings\\.gradle\\.kts|gradle\\.properties|package\\.json|"
                        + "package-lock\\.json|pnpm-lock\\.yaml|yarn\\.lock|tsconfig\\.json|cargo\\.toml|cargo\\.lock|.*\\.cabal).*")) {
            return false;
        }
        return lower.contains(">") || lower.contains("sed -i") || lower.contains("perl -pi") || lower.contains(" tee ") || lower.startsWith("tee ") || lower.contains(" rm ")
                || lower.startsWith("rm ") || lower.contains(" mv ") || lower.startsWith("mv ") || lower.contains(" cp ") || lower.startsWith("cp ");
    }

    private static String invalidPathError(String path) {
        String safePath = SECRET_MATERIAL_POLICY.assess(path, new byte[0], HyperionSecretMaterialPolicy.Origin.TOOL_OBSERVATION).safePath();
        return "ERROR: invalid path '" + safePath + "'. Use a workspace-relative path containing only letters, digits, '_', '.', '/', '-' and no '..'.";
    }

    /**
     * Normalises a model-supplied path to a workspace-relative one and validates it against a conservative source-file path allowlist
     * ({@code [a-zA-Z0-9_./-]} and no {@code ..}), which rejects shell metacharacters including quotes.
     *
     * @return the relative path, or {@code null} if it is unsafe
     */
    static String workspaceRelativePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.trim();
        if (trimmed.startsWith(WORKSPACE + "/")) {
            trimmed = trimmed.substring((WORKSPACE + "/").length());
        }
        if (trimmed.startsWith("/") || trimmed.contains("..") || !trimmed.matches("[a-zA-Z0-9_./-]+")) {
            return null;
        }
        return trimmed;
    }
}
