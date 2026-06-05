package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;

/**
 * The read/write/edit/bash tools the exercise-generation agent calls, bound to one sandbox session.
 * <p>
 * The agent has a full shell because correctness is never judged from what these tools report — that is the out-of-band verifier's job — so the shell's power cannot be used to
 * fake a passing exercise. Created per session (it holds the session id), so it is intentionally not a Spring bean.
 */
public class SandboxAgentTools {

    private static final String WORKSPACE = "/workspace";

    private static final Duration FILE_OP_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration BASH_TIMEOUT = Duration.ofMinutes(5);

    /** Directory inside the sandbox (outside /workspace, so it is never picked up by repository extraction) where each command's full output is spilled. */
    private static final String SPILL_DIR = "/tmp/hyperion";

    /**
     * Bytes of a command's output tail returned to the agent inline. Kept under the agent's downstream per-tool-result context cap (AgentLoopRunner.MAX_TOOL_RESPONSE_CHARS =
     * 12000)
     * so the result is never re-truncated there — the marker the agent sees is therefore always truthful — while the complete output stays in the spill file for deeper inspection.
     */
    private static final int BASH_TAIL_BYTES = 10_000;

    /**
     * Per-command spill-file size ceiling enforced via {@code ulimit -f} (units are 512-byte blocks): 65536 * 512 = 32 MB, so a runaway command (e.g. {@code yes}) cannot fill the
     * container disk before the timeout fires.
     */
    private static final int SPILL_ULIMIT_BLOCKS = 65_536;

    /**
     * First line the bash wrapper prints, carrying the real exit code and total size; parsed and stripped by {@link #bash(String)} (the container exec's own exit code
     * reflects the wrapper, not the command).
     */
    private static final Pattern BASH_META = Pattern.compile("^__HYP_META__ rc=(-?\\d+) bytes=(\\d+) lines=(\\d+)$");

    /**
     * Matches the {@code List.toString()} rendering of a JSON argv array that Spring AI produces when the model sends {@code {"command":["bash","-lc","ls -R"]}} — an opening
     * {@code [} immediately followed by a non-space token and containing a comma, closing with {@code ]}. A real POSIX {@code [ -f x ]} test has a SPACE right after the bracket,
     * so
     * it does not match; a single-element list {@code [foo]} has no comma, so it does not match either (and would run harmlessly as a shell glob anyway).
     */
    private static final Pattern MANGLED_ARRAY = Pattern.compile("^\\[\\S.*,.*]$", Pattern.DOTALL);

    private final InteractiveSandbox sandbox;

    private final String sessionId;

    /**
     * Monotonic per-command counter for spill-file names. A plain field (no synchronization) is safe: each session has its own {@code SandboxAgentTools} and the agent loop calls
     * the tools serially within a session; different sessions run in different containers, so identical sequence numbers never collide.
     */
    private int bashSequence = 0;

    public SandboxAgentTools(InteractiveSandbox sandbox, String sessionId) {
        this.sandbox = sandbox;
        this.sessionId = sessionId;
    }

    /**
     * Reads a workspace file.
     *
     * @param path the workspace-relative path to read
     * @return the file content, or an actionable error message if the path is invalid or unreadable
     */
    @Tool(name = "read_file", description = "Read a UTF-8 text file in the workspace and return its full contents. The path is workspace-relative (e.g. 'solution/src/Calculator.java'). Prefer this over 'cat'. For a large file, or to find one thing, use bash with grep/sed instead of reading the whole file.")
    public String readFile(@ToolParam(description = "workspace-relative path to read, e.g. 'tests/test/sorting/SortTest.java'") String path) {
        String safe = workspaceRelativePath(path);
        if (safe == null) {
            return invalidPathError(path);
        }
        SandboxExecResult result = sandbox.exec(sessionId, FILE_OP_TIMEOUT, "cat", WORKSPACE + "/" + safe);
        return result.isSuccess() ? result.stdout() : "ERROR: could not read '" + safe + "': " + result.combinedOutput();
    }

    /**
     * Creates or overwrites a workspace file.
     *
     * @param path    the workspace-relative path to write
     * @param content the complete new file content
     * @return a confirmation, or an actionable error message
     */
    @Tool(name = "write_file", description = "Write the full content of a workspace file, creating it (and any parent directories) or overwriting it if it exists. Use only for new files or complete rewrites; for small changes to an existing file use edit_file. The path is workspace-relative.")
    public String writeFile(@ToolParam(description = "workspace-relative path to write, e.g. 'solution/palindrome.py'") String path,
            @ToolParam(description = "the complete new content of the file") String content) {
        String safe = workspaceRelativePath(path);
        if (safe == null) {
            return invalidPathError(path);
        }
        // The content is transferred base64-encoded so arbitrary source code (quotes, newlines) is written verbatim; the path is allowlisted above so it cannot break the shell.
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
    @Tool(name = "edit_file", description = "Replace an exact, unique snippet in an existing workspace file. 'oldText' must match the file byte-for-byte including whitespace and newlines, and must occur exactly once — keep it as small as possible while still unique, do not pad with unchanged lines. Prefer this over write_file for small, targeted changes.")
    public String editFile(@ToolParam(description = "workspace-relative path to edit") String path,
            @ToolParam(description = "the exact existing text to replace, byte-for-byte; must be unique in the file") String oldText,
            @ToolParam(description = "the replacement text") String newText) {
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
     * Runs a shell command in the workspace root.
     *
     * @param command the shell command to run, as a single string
     * @return the exit status followed by the combined stdout/stderr
     */
    @Tool(name = "bash", description = "Run a shell command in the workspace, e.g. {\"command\":\"ls -R\"}. Send the command as a single string (NOT a JSON array). Returns its exit code plus combined stdout/stderr. Use it to run 'sh verify.sh solution' / 'sh verify.sh template', inspect the project, and debug. Long output is truncated to the LAST 10000 characters (build failures and the verify.sh HYPERION_COLLECTED line are at the end); the COMPLETE output is saved in the sandbox to /tmp/hyperion/bash-<n>.log, so read earlier parts with sed/grep/head/tail on that file. After a verify.sh run the test reports are collected under /opt/hyperion/reports/<solution|template>/ — grep them for exact test names and pass/fail. Prefer grep/sed here over re-reading whole files.")
    public String bash(
            @ToolParam(description = "the shell command to run, as ONE string (not a JSON array), e.g. 'sh verify.sh solution', 'ls -R', or 'grep -n sort tests/test/sorting/SortTest.java'") String command) {
        if (command == null || command.isBlank()) {
            return "exit=64\nNo command provided. Put the shell command in the \"command\" field as a single string, e.g. {\"command\": \"ls -R\"}.";
        }
        // The model often sends an argv array, e.g. {"command":["bash","-lc","ls -R"]}. Spring AI's MethodToolCallback coerces a JSON array to a String via List.toString(),
        // yielding the literal "[bash, -lc, ls -R]". Running that through the shell does NOT do what the model intended (the first word "[bash," is not a command), yet the failure
        // is easy for the model to misread, so it blindly retries the array form. Detect that exact shape up front and reject it LOUDLY with a non-zero exit and a single
        // corrective
        // instruction, so the agent re-sends a plain string instead of thrashing. A genuine POSIX test starts with "[ " (a space after the bracket), so it never matches.
        if (isMangledArrayCommand(command)) {
            return "exit=2\nThe command must be a single shell string, e.g. {\"command\":\"ls -R\"}. You sent a JSON array, which I cannot run. Re-send it as one string.";
        }
        // The model repeatedly reaches for a Codex-style `apply_patch` — both as a non-existent tool and as a bash command (`apply_patch <<'PATCH' … PATCH`). A bash `apply_patch`
        // is not installed, so the shell would exit 127 with "not found" but leave the workspace UNCHANGED while the model believes the edit landed, and it thrashes for many
        // turns.
        // Short-circuit it here with a LOUD, non-zero result and never touch the sandbox, so the agent observes a clear failure and switches to write_file / edit_file.
        if (isApplyPatchInvocation(command)) {
            return "exit=2\napply_patch is NOT available. Use write_file (new file / full rewrite) or edit_file (exact unique snippet) instead.";
        }
        int sequence = bashSequence++;
        String logPath = SPILL_DIR + "/bash-" + sequence + ".log";
        // The command runs in a SUBSHELL so that an `exit` inside it (e.g. from verify.sh) cannot abort this wrapper before it reports the exit code and tail. Its combined
        // stdout+stderr is redirected to the spill file (preserving real interleaving), so the real exit code is captured directly from `$?` — we do not pipe (POSIX `sh` has no
        // PIPESTATUS, so a pipe would report the wrong command's status). `ulimit -f` caps the spill file size and `</dev/null` stops a command that reads stdin from hanging until
        // the timeout. Only a small meta line plus the output tail are streamed back; the full output stays in the spill file for the agent to inspect.
        // `wc` output is piped through `tr -d` because some implementations right-pad the count with spaces; stripping them keeps the meta line in the exact shape the parser
        // expects, so the authoritative exit code is never lost to a formatting quirk (which would otherwise make every command look like it exited 0).
        String script = "LOG=" + logPath + "\n" + "mkdir -p " + SPILL_DIR + "\n" + "( ulimit -f " + SPILL_ULIMIT_BLOCKS + " 2>/dev/null; cd " + WORKSPACE + " && " + command
                + " ) </dev/null > \"$LOG\" 2>&1\n" + "rc=$?\n" + "bytes=$(wc -c < \"$LOG\" | tr -d ' \\t')\n" + "lines=$(wc -l < \"$LOG\" | tr -d ' \\t')\n"
                + "printf '__HYP_META__ rc=%s bytes=%s lines=%s\\n' \"$rc\" \"$bytes\" \"$lines\"\n" + "tail -c " + BASH_TAIL_BYTES + " \"$LOG\"\n";
        SandboxExecResult result = sandbox.exec(sessionId, BASH_TIMEOUT, "sh", "-c", script);
        if (result.timedOut()) {
            // On timeout the wrapper never reached the meta/tail step, so no output is on the wire — but the partial output is in the spill file the agent can read next.
            return "exit=timeout (the command exceeded its time budget)\n[Partial output was written in the sandbox to " + logPath + " — read it with: tail -n 200 " + logPath
                    + "]";
        }
        return composeBashOutput(result, logPath);
    }

    /**
     * Composes the model-facing result from the wrapper's output: parses the leading {@code __HYP_META__} line for the real exit code and total size, then returns the exit code,
     * the output tail, and — when the output was larger than the tail — a self-describing marker pointing at the spill file (matching the pi reference's "full output saved to …"
     * markers, but readable with the agent's own tools).
     */
    private String composeBashOutput(SandboxExecResult result, String logPath) {
        String output = result.combinedOutput() == null ? "" : result.combinedOutput();
        int newline = output.indexOf('\n');
        Matcher meta = BASH_META.matcher(newline < 0 ? output.strip() : output.substring(0, newline).strip());
        if (!meta.matches()) {
            // The meta line is missing only if the wrapper itself failed unexpectedly; still hand the model something actionable rather than nothing.
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
     * Signals that the exercise is complete. The agent loop ends the session when this is called, and the authoritative verifier then decides acceptance independently.
     *
     * @param summary an optional one-line summary of what was created or changed
     * @return a confirmation that the work was submitted for verification
     */
    @Tool(name = "submit", description = "Submit the finished exercise for authoritative verification and end the session. Only call this after 'sh verify.sh solution' exits 0 with failures=0 and errors=0, and 'sh verify.sh template' exits non-zero with at least one failure/error at the SAME tests count. Stop immediately after calling it.")
    public String submit(@ToolParam(required = false, description = "one-line summary of what you created or changed") String summary) {
        return "Submitted for verification" + (summary == null || summary.isBlank() ? "." : ": " + summary);
    }

    /**
     * Detects whether a bash command is an {@code apply_patch} invocation — the first shell word is {@code apply_patch} (optionally with a leading heredoc/redirect form such as
     * {@code apply_patch <<'PATCH'}), in any of the wrappers the model emits (a bare call, a heredoc, or after a redirect). A leading {@code ./} or path prefix is tolerated.
     * Matching
     * the first token rather than a substring avoids flagging an innocent command that merely mentions the word (e.g. {@code grep apply_patch}).
     *
     * @param command the effective shell command
     * @return {@code true} if the command would run {@code apply_patch}
     */
    static boolean isApplyPatchInvocation(String command) {
        String trimmed = command.strip();
        // Strip a leading "./" or directory prefix on the program name so "./apply_patch" / "/usr/bin/apply_patch" are still caught.
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
     * Detects whether a bash command is the mangled {@code List.toString()} form of a JSON argv array (e.g. {@code [bash, -lc, ls -R]}) that Spring AI coerces when the model sends
     * {@code {"command":["bash","-lc","ls -R"]}}. This is NOT a runnable shell command, so the tool rejects it loudly instead of running garbage. A genuine POSIX {@code [ -f x ]}
     * test does not match because it has a space immediately after the {@code [}.
     *
     * @param command the effective shell command
     * @return {@code true} if the command is the rendered form of a JSON array rather than a real shell string
     */
    static boolean isMangledArrayCommand(String command) {
        return MANGLED_ARRAY.matcher(command.strip()).matches();
    }

    private static String invalidPathError(String path) {
        return "ERROR: invalid path '" + path + "'. Use a workspace-relative path containing only letters, digits, '_', '.', '/', '-' and no '..'.";
    }

    /**
     * Normalises a model-supplied path to a workspace-relative one and validates it against the same allowlist the build agent uses for container paths
     * ({@code [a-zA-Z0-9_*./-]} and no {@code ..}), which by construction rejects shell metacharacters including quotes.
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
        if (trimmed.startsWith("/") || trimmed.contains("..") || !trimmed.matches("[a-zA-Z0-9_*./-]+")) {
            return null;
        }
        return trimmed;
    }
}
