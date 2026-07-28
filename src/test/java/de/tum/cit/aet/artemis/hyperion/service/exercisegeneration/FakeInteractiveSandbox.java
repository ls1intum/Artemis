package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpecDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;

/**
 * The one in-memory {@link InteractiveSandbox} the Hyperion generation tests run against, replacing the six near-identical hand-rolled doubles that used to live one per test
 * class.
 * <p>
 * Those six agreed on everything that is not interesting — a fixed session handle, inert copy/destroy, {@code cat} served from a map — and disagreed silently on everything that
 * is, which is how {@link InteractiveSandbox#resetSession} came to be unimplemented in all of them: it was added to the interface as a throwing {@code default}, so every double
 * kept compiling while modelling a sandbox that cannot reset, which no production implementation is.
 * <p>
 * Bespoke behaviour is configured, not forked: the file map and {@link #returning(SandboxExecResultDTO)} cover the simple cases, and a test whose sandbox must answer probes
 * (find/grep/diff/verify.sh) overrides the single {@link #respond(String[])} seam while still inheriting the recording and the inert half.
 */
public class FakeInteractiveSandbox implements InteractiveSandbox {

    /** The handle {@link #createSession} hands out; every operation accepts any handle, because no test under this fake is about handle routing. */
    public static final String SESSION_ID = "fake-session";

    private static final SandboxExecResultDTO EMPTY_SUCCESS = new SandboxExecResultDTO(0, "", "", false);

    /** File contents keyed by absolute container path, served by {@code cat <path>}; an absent path reads back as a failed {@code cat}, exactly as a real container would. */
    private final Map<String, String> files = new LinkedHashMap<>();

    private final List<String> executedCommands = new ArrayList<>();

    private SandboxExecResultDTO defaultResult = EMPTY_SUCCESS;

    @Nullable
    private String lastScript;

    @Nullable
    private String lastWrittenBase64;

    private int resetSessionCount;

    private int destroySessionCount;

    /** A fake whose every command returns {@code result}, for tests that assert on the script rather than on the sandbox's answer. */
    public static FakeInteractiveSandbox returning(SandboxExecResultDTO result) {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        sandbox.defaultResult = result;
        return sandbox;
    }

    @Override
    public String createSession(SandboxSessionSpecDTO spec) {
        return SESSION_ID;
    }

    /**
     * Records the command, then answers it. Final so that the recording below can never be lost by a subclass that overrides {@code exec} and forgets to call {@code super}: the
     * extension point is {@link #respond(String[])}.
     */
    @Override
    public final SandboxExecResultDTO exec(String sessionId, Duration timeout, String... command) {
        executedCommands.add(String.join(" ", command));
        lastScript = command.length == 0 ? null : command[command.length - 1];
        captureBase64Write(command);
        SandboxExecResultDTO scripted = respond(command);
        return scripted == null ? defaultResult : scripted;
    }

    /**
     * How this sandbox answers one command, or {@code null} to fall through to the configured default result.
     *
     * @param command the command and its arguments, exactly as the caller passed them
     * @return the scripted result, or {@code null} for "no opinion"
     */
    @Nullable
    protected SandboxExecResultDTO respond(String[] command) {
        return catFromFiles(command);
    }

    /** Serves {@code cat <absolute path>} from the file map; {@code null} for any other command. */
    @Nullable
    protected final SandboxExecResultDTO catFromFiles(String[] command) {
        if (command.length < 2 || !"cat".equals(command[0])) {
            return null;
        }
        String content = files.get(command[1]);
        return content == null ? new SandboxExecResultDTO(1, "", "cat: " + command[1] + ": No such file or directory", false) : new SandboxExecResultDTO(0, content, "", false);
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

    /** The mutable file map; a test seeds it with the absolute container paths the code under test will {@code cat}. */
    public Map<String, String> files() {
        return files;
    }

    /** Seeds one file and returns this fake, so a fixture reads as one expression. */
    public FakeInteractiveSandbox withFile(String absolutePath, String content) {
        files.put(absolutePath, content);
        return this;
    }

    /** Every command run so far, each joined by single spaces in the order it was issued. */
    public List<String> executedCommands() {
        return List.copyOf(executedCommands);
    }

    public int execCount() {
        return executedCommands.size();
    }

    /** The last argument of the most recent command — for {@code sh -c <script>} that is the script itself. {@code null} until a command runs. */
    @Nullable
    public String lastScript() {
        return lastScript;
    }

    /** The base64 payload of the most recent write script, {@code null} until one runs. */
    @Nullable
    public String lastWrittenBase64() {
        return lastWrittenBase64;
    }

    /** The UTF-8 text of the most recent write, {@code null} until one runs. */
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
