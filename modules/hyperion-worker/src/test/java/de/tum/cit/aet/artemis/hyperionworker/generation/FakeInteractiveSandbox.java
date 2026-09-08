package de.tum.cit.aet.artemis.hyperionworker.generation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperionworker.sandbox.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.SandboxExecResult;

/**
 * The in-memory {@link InteractiveSandbox} the Hyperion generation tests run against: a fixed session handle, inert copy/destroy, and {@code cat} served from a file map.
 * <p>
 * Bespoke behaviour is configured rather than forked. The file map and {@link #returning(SandboxExecResult)} cover the simple cases; a test whose sandbox must answer probes
 * (find/grep/diff/verify.sh) overrides the single {@link #respond(String[])} seam and still inherits the recording and the inert half.
 */
public class FakeInteractiveSandbox implements InteractiveSandbox {

    /** The handle {@link #createSession} hands out; every operation accepts any handle, since no test under this fake is about handle routing. */
    public static final String SESSION_ID = "fake-session";

    private static final SandboxExecResult EMPTY_SUCCESS = new SandboxExecResult(0, "", "", false);

    /** File contents keyed by absolute container path, served by {@code cat <path>}; an absent path reads back as a failed {@code cat}, exactly as a real container would. */
    private final Map<String, String> files = new LinkedHashMap<>();

    private final List<String> executedCommands = new ArrayList<>();

    private SandboxExecResult defaultResult = EMPTY_SUCCESS;

    @Nullable
    private String lastScript;

    @Nullable
    private String lastWrittenBase64;

    private int resetSessionCount;

    private int destroySessionCount;

    /**
     * Creates a fake with a fixed command response.
     *
     * @param result fallback response for each command
     * @return the configured sandbox
     */
    public static FakeInteractiveSandbox returning(SandboxExecResult result) {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        sandbox.defaultResult = result;
        return sandbox;
    }

    @Override
    public String createSession() {
        return SESSION_ID;
    }

    /** Records the command, then answers it. Final so a subclass cannot drop the recording; the extension point is {@link #respond(String[])}. */
    @Override
    public final SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
        executedCommands.add(String.join(" ", command));
        lastScript = command.length == 0 ? null : command[command.length - 1];
        captureBase64Write(command);
        SandboxExecResult scripted = respond(command);
        return scripted == null ? defaultResult : scripted;
    }

    /**
     * How this sandbox answers one command, or {@code null} to fall through to the configured default result.
     *
     * @param command the command and its arguments, exactly as the caller passed them
     * @return the scripted result, or {@code null} for "no opinion"
     */
    @Nullable
    protected SandboxExecResult respond(String[] command) {
        return catFromFiles(command);
    }

    /** Serves {@code cat <absolute path>} from the file map; {@code null} for any other command. */
    @Nullable
    protected final SandboxExecResult catFromFiles(String[] command) {
        if (command.length < 2 || !"cat".equals(command[0])) {
            return null;
        }
        String content = files.get(command[1]);
        return content == null ? new SandboxExecResult(1, "", "cat: " + command[1] + ": No such file or directory", false) : new SandboxExecResult(0, content, "", false);
    }

    /** The agent tools write files by piping a base64 payload into the target path; capturing it lets a test assert exactly what was persisted. */
    private void captureBase64Write(String[] command) {
        if (command.length != 3 || !"sh".equals(command[0]) || !command[2].contains("| base64 -d >")) {
            return;
        }
        int start = command[2].indexOf("echo '") + "echo '".length();
        int end = command[2].indexOf('\'', start);
        if (start >= "echo '".length() && end > start) {
            lastWrittenBase64 = command[2].substring(start, end);
        }
    }

    @Override
    public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
    }

    @Override
    @Nullable
    public TarArchiveInputStream copyOut(String sessionId, String path) {
        return null;
    }

    @Override
    public void resetSession(String sessionId) {
        resetSessionCount++;
    }

    @Override
    public void destroySession(String sessionId) {
        destroySessionCount++;
    }

    /**
     * @return mutable file contents keyed by absolute container path
     */
    public Map<String, String> files() {
        return files;
    }

    /**
     * Seeds one file.
     *
     * @param absolutePath container path
     * @param content      file contents
     * @return this sandbox
     */
    public FakeInteractiveSandbox withFile(String absolutePath, String content) {
        files.put(absolutePath, content);
        return this;
    }

    /**
     * @return commands in invocation order, with arguments joined by spaces
     */
    public List<String> executedCommands() {
        return List.copyOf(executedCommands);
    }

    public int execCount() {
        return executedCommands.size();
    }

    /**
     * @return the last command argument, or null before any command runs
     */
    @Nullable
    public String lastScript() {
        return lastScript;
    }

    /**
     * @return the last write's base64 payload, or null before any write
     */
    @Nullable
    public String lastWrittenBase64() {
        return lastWrittenBase64;
    }

    /**
     * @return the last write's decoded text, or null before any write
     */
    @Nullable
    public String lastWrittenText() {
        return lastWrittenBase64 == null ? null : new String(java.util.Base64.getDecoder().decode(lastWrittenBase64), StandardCharsets.UTF_8);
    }

    public int resetSessionCount() {
        return resetSessionCount;
    }

    public int destroySessionCount() {
        return destroySessionCount;
    }
}
