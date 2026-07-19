package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ExerciseIntegrityGate;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * The file, shell, and verification tools the exercise-generation agent calls, bound to one sandbox session. Created per session (holds the session id), so not a Spring bean.
 * <p>
 * The agent has a full shell safely because correctness is never judged from what these tools report. The {@code verify} tool runs the same differential as the authoritative
 * post-loop verifier (two fresh builds parsed with the production parsers) and returns structured feedback, but it is advisory only; the post-loop verifier decides mechanical
 * validity.
 */
public class SandboxAgentTools {

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    private static final String WORKSPACE = "/workspace";

    private static final Duration FILE_OP_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration BASH_TIMEOUT = Duration.ofMinutes(5);

    /** Spill directory inside the sandbox, outside /workspace so it is never picked up by repository extraction. */
    private static final String SPILL_DIR = "/tmp/hyperion";

    /**
     * Bytes of the output tail returned inline. Kept under the downstream per-tool-result cap (AgentLoopRunner.MAX_TOOL_RESPONSE_CHARS = 12000) so the result is never re-truncated
     * there and the truncation marker the agent sees stays truthful; the full output lives in the spill file.
     */
    private static final int BASH_TAIL_BYTES = 10_000;

    /** Per-command spill-file ceiling via {@code ulimit -f} (512-byte blocks): 65536 * 512 = 32 MB, so a runaway command cannot fill the container disk before the timeout. */
    private static final int SPILL_ULIMIT_BLOCKS = 65_536;

    /** First line the bash wrapper prints, carrying the real exit code and size (the container exec's own exit code reflects the wrapper, not the command). */
    private static final Pattern BASH_META = Pattern.compile("^__HYP_META__ rc=(-?\\d+) bytes=(\\d+) lines=(\\d+)$");

    /**
     * Matches the {@code List.toString()} render of a JSON argv array (e.g. {@code [bash, -lc, ls -R]}) Spring AI produces from {@code {"command":[...]}}. A POSIX {@code [ -f x ]}
     * test has a space after the bracket so it does not match; a single-element {@code [foo]} has no comma so it does not either.
     */
    private static final Pattern MANGLED_ARRAY = Pattern.compile("^\\[\\S.*,.*]$", Pattern.DOTALL);

    private final InteractiveSandbox sandbox;

    private final String sessionId;

    /** The authoritative verifier, reused by the {@code verify} tool to run the same differential as the post-loop mechanical gate; {@code null} disables the tool in tests. */
    @Nullable
    private final DifferentialVerificationService verifier;

    /** The exercise whose per-language {@code verify.sh} and SCA configuration the {@code verify} tool's differential uses; {@code null} disables the tool in tests. */
    @Nullable
    private final ProgrammingExercise exercise;

    /** Seeded test files let the in-loop verifier distinguish untouched legacy tests from files authored or changed in this run. */
    private final Map<String, String> seedTestsFiles;

    private final boolean adaptation;

    /** Per-command spill-file counter; unsynchronized is safe — the agent loop calls the tools serially within a session and each session has its own instance and container. */
    private int bashSequence = 0;

    private boolean sandboxSessionTerminated;

    /**
     * @param sandbox   the sandbox session the tools operate on
     * @param sessionId the session handle
     * @param verifier  the authoritative verifier the {@code verify} tool reuses for the in-loop self-check
     * @param exercise  the exercise whose {@code verify.sh}/SCA config the {@code verify} tool's differential uses
     */
    public SandboxAgentTools(InteractiveSandbox sandbox, String sessionId, DifferentialVerificationService verifier, ProgrammingExercise exercise) {
        this(sandbox, sessionId, verifier, exercise, Map.of(), false);
    }

    public SandboxAgentTools(InteractiveSandbox sandbox, String sessionId, DifferentialVerificationService verifier, ProgrammingExercise exercise,
            Map<String, String> seedTestsFiles, boolean adaptation) {
        this.sandbox = sandbox;
        this.sessionId = sessionId;
        this.verifier = verifier;
        this.exercise = exercise;
        this.seedTestsFiles = Map.copyOf(seedTestsFiles);
        this.adaptation = adaptation;
    }

    /**
     * Verify-free constructor: the verifier and exercise are absent, so the {@code verify} tool is never wired. Used by unit tests of the file/shell tools.
     *
     * @param sandbox   the sandbox session the tools operate on
     * @param sessionId the session handle
     */
    SandboxAgentTools(InteractiveSandbox sandbox, String sessionId) {
        this(sandbox, sessionId, null, null, Map.of(), false);
    }

    /**
     * Reads a workspace file.
     *
     * @param path the workspace-relative path to read
     * @return the file content, or an actionable error message if the path is invalid or unreadable
     */
    @Tool(name = "read_file", description = AgentToolDescriptions.READ_FILE)
    public String readFile(@ToolParam(description = AgentToolDescriptions.READ_FILE_PATH) String path) {
        String safe = workspaceRelativePath(path);
        if (safe == null) {
            return invalidPathError(path);
        }
        HyperionSecretMaterialPolicy.Assessment pathAssessment = SECRET_MATERIAL_POLICY.assess(safe, new byte[0], HyperionSecretMaterialPolicy.Origin.TOOL_OBSERVATION);
        if (!pathAssessment.isSafe()) {
            return SECRET_MATERIAL_POLICY.blockedObservation(pathAssessment);
        }
        SandboxExecResult result = sandbox.exec(sessionId, FILE_OP_TIMEOUT, "cat", WORKSPACE + "/" + safe);
        String observation = result.isSuccess() ? result.stdout() : "ERROR: could not read '" + safe + "': " + result.combinedOutput();
        return screenObservation(safe, observation);
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
            return "ERROR: write only problem-statement.md or files inside solution/, template/, or tests/. Workspace build infrastructure is managed by Artemis.";
        }
        if (isManagedBuildInfrastructurePath(safe)) {
            return immutableHarnessError(safe);
        }
        // base64-encode the content so arbitrary source (quotes, newlines) is written verbatim; the path is allowlisted above so it cannot break the shell.
        String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String target = WORKSPACE + "/" + safe;
        String script = "mkdir -p \"$(dirname '" + target + "')\" && echo '" + encoded + "' | base64 -d > '" + target + "'";
        SandboxExecResult result = sandbox.exec(sessionId, FILE_OP_TIMEOUT, "sh", "-c", script);
        return result.isSuccess() ? "Wrote " + content.length() + " characters to " + safe : "ERROR: could not write '" + safe + "': " + result.combinedOutput();
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
        int first = current.indexOf(oldText);
        if (first < 0) {
            return "ERROR: the provided oldText was not found in '" + safe + "'. Read the file again to get the exact current text.";
        }
        if (current.indexOf(oldText, first + 1) >= 0) {
            return "ERROR: the provided oldText occurs more than once in '" + safe + "'. Provide more surrounding context to make it unique.";
        }
        String updated = current.substring(0, first) + newText + current.substring(first + oldText.length());
        return writeFile(safe, updated);
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
            return "ERROR: delete only problem-statement.md or files inside solution/, template/, or tests/. Workspace build infrastructure is managed by Artemis.";
        }
        if (isManagedBuildInfrastructurePath(safe)) {
            return immutableHarnessError(safe);
        }
        SandboxExecResult result = sandbox.exec(sessionId, FILE_OP_TIMEOUT, "rm", "-f", "--", WORKSPACE + "/" + safe);
        return result.isSuccess() ? "Deleted " + safe : "ERROR: could not delete '" + safe + "': " + result.combinedOutput();
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
        int sequence = bashSequence++;
        String logPath = SPILL_DIR + "/bash-" + sequence + ".log";
        // Run in a subshell so an `exit` inside (e.g. from verify.sh) cannot abort this wrapper before it reports the code and tail. Combined output is redirected, not piped
        // (POSIX
        // sh has no PIPESTATUS), so the real exit code comes from `$?`; `ulimit -f` caps the spill size and `</dev/null` stops a stdin-reading command from hanging until the
        // timeout. `wc` is run through `tr -d` because some implementations pad the count with spaces, which would corrupt the meta line and lose the authoritative exit code.
        String script = "LOG=" + logPath + "\n" + "mkdir -p " + SPILL_DIR + "\n" + "( ulimit -f " + SPILL_ULIMIT_BLOCKS + " 2>/dev/null; cd " + WORKSPACE + " && " + command
                + " ) </dev/null > \"$LOG\" 2>&1\n" + "rc=$?\n" + "bytes=$(wc -c < \"$LOG\" | tr -d ' \\t')\n" + "lines=$(wc -l < \"$LOG\" | tr -d ' \\t')\n"
                + "printf '__HYP_META__ rc=%s bytes=%s lines=%s\\n' \"$rc\" \"$bytes\" \"$lines\"\n" + "tail -c " + BASH_TAIL_BYTES + " \"$LOG\"\n";
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
        if (bytes <= BASH_TAIL_BYTES) {
            return "exit=" + rc + "\n" + body;
        }
        return "exit=" + rc + "\n" + body + "\n\n[Showing the last " + BASH_TAIL_BYTES + " of " + bytes + " bytes (" + lines + " lines total). Full output saved in the sandbox at "
                + logPath + " — read more with: tail -n 200 " + logPath + "  (or sed -n '1,200p' " + logPath + ", grep PATTERN " + logPath + ")]";
    }

    /** Last-resort character tail used only when the wrapper's meta line is unexpectedly absent. */
    private static String charTail(String output) {
        if (output.length() <= BASH_TAIL_BYTES) {
            return output;
        }
        return "[showing the last " + BASH_TAIL_BYTES + " of " + output.length() + " characters]\n" + output.substring(output.length() - BASH_TAIL_BYTES);
    }

    /**
     * Runs the authoritative differential self-check and returns structured, actionable feedback.
     *
     * @return the agent-readable observation (solution/template test outcomes, exact test names to bind, current verdict), or an error message if the verifier is unavailable
     */
    @Tool(name = "verify", description = AgentToolDescriptions.VERIFY)
    public String verify() {
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
     * Signals that the exercise is complete. The agent loop ends the session when this is called, and the authoritative verifier then decides mechanical validity independently.
     *
     * @param summary an optional one-line summary of what was created or changed
     * @return a confirmation that the work was submitted for verification
     */
    @Tool(name = "submit", description = AgentToolDescriptions.SUBMIT)
    public String submit(@ToolParam(required = false, description = AgentToolDescriptions.SUBMIT_SUMMARY) String summary) {
        return "Submitted for verification" + (summary == null || summary.isBlank() ? "." : ": " + summary);
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

    private static boolean isWritableGenerationPath(String safe) {
        return safe.equals("problem-statement.md") || safe.startsWith("solution/") || safe.startsWith("template/") || safe.startsWith("tests/");
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
