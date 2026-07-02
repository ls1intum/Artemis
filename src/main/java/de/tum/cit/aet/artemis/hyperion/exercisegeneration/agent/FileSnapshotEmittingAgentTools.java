package de.tum.cit.aet.artemis.hyperion.exercisegeneration.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import de.tum.cit.aet.artemis.hyperion.dto.HyperionFileSnapshotDTO;

/**
 * A thin emitting decorator around {@link SandboxAgentTools}: it re-exposes the exact same {@code @Tool} surface (so the model sees an identical toolset) but, whenever
 * {@code write_file} or {@code edit_file} succeeds, it emits a whole-file {@link HyperionFileSnapshotDTO} to the given sink for live streaming to the triggering instructor.
 * <p>
 * It never touches the tool bodies: read/bash/verify/submit are pure delegations, and the two write paths delegate to the inner tools first and only emit on success. The whole
 * file content is already in memory here — {@code write_file} receives it as an argument, and for {@code edit_file} the decorator reconstructs it from the last snapshot it holds
 * for that path (falling back to a single read only if it has no cached copy), so no extra sandbox read-back is needed on the common path.
 * <p>
 * Created per session and driven serially by the single-threaded agent loop, so the per-path cache needs no synchronisation.
 */
public class FileSnapshotEmittingAgentTools implements TurnAware {

    private static final Logger log = LoggerFactory.getLogger(FileSnapshotEmittingAgentTools.class);

    /** A successful {@code write_file}/{@code edit_file} result starts with this marker (see {@link SandboxAgentTools#writeFile}); an error starts with {@code ERROR}. */
    private static final String WRITE_SUCCESS_PREFIX = "Wrote ";

    private final SandboxAgentTools delegate;

    private final Consumer<HyperionFileSnapshotDTO> snapshotSink;

    /** The last full content streamed per (normalised) path, so an {@code edit_file} can rebuild the whole file without a sandbox read and identical writes can be coalesced. */
    private final Map<String, String> latestContentByPath = new LinkedHashMap<>();

    private int currentTurn = 0;

    /**
     * @param delegate     the underlying tools that actually operate on the sandbox
     * @param snapshotSink receives a whole-file snapshot on every successful write; must not be {@code null}
     */
    public FileSnapshotEmittingAgentTools(SandboxAgentTools delegate, Consumer<HyperionFileSnapshotDTO> snapshotSink) {
        this.delegate = delegate;
        this.snapshotSink = snapshotSink;
    }

    @Override
    public void onTurn(int turn) {
        this.currentTurn = turn;
    }

    @Tool(name = "read_file", description = "Read a UTF-8 text file in the workspace and return its full contents. The path is workspace-relative (e.g. 'solution/src/Calculator.java'). Prefer this over 'cat'. For a large file, or to find one thing, use bash with grep/sed instead of reading the whole file.")
    public String readFile(@ToolParam(description = "workspace-relative path to read, e.g. 'tests/test/sorting/SortTest.java'") String path) {
        return delegate.readFile(path);
    }

    @Tool(name = "write_file", description = "Write the full content of a workspace file, creating it (and any parent directories) or overwriting it if it exists. Use only for new files or complete rewrites; for small changes to an existing file use edit_file. The path is workspace-relative.")
    public String writeFile(@ToolParam(description = "workspace-relative path to write, e.g. 'solution/palindrome.py'") String path,
            @ToolParam(description = "the complete new content of the file") String content) {
        String result = delegate.writeFile(path, content);
        if (isSuccess(result)) {
            String safe = SandboxAgentTools.workspaceRelativePath(path);
            if (safe != null) {
                String action = latestContentByPath.containsKey(safe) ? HyperionFileSnapshotDTO.ACTION_EDIT : HyperionFileSnapshotDTO.ACTION_CREATE;
                emit(safe, action, content);
            }
        }
        return result;
    }

    @Tool(name = "edit_file", description = "Replace an exact, unique snippet in an existing workspace file. 'oldText' must match the file byte-for-byte including whitespace and newlines, and must occur exactly once — keep it as small as possible while still unique, do not pad with unchanged lines. Prefer this over write_file for small, targeted changes.")
    public String editFile(@ToolParam(description = "workspace-relative path to edit") String path,
            @ToolParam(description = "the exact existing text to replace, byte-for-byte; must be unique in the file") String oldText,
            @ToolParam(description = "the replacement text") String newText) {
        String result = delegate.editFile(path, oldText, newText);
        if (isSuccess(result)) {
            String safe = SandboxAgentTools.workspaceRelativePath(path);
            if (safe != null) {
                emit(safe, HyperionFileSnapshotDTO.ACTION_EDIT, reconstructEditedContent(path, safe, oldText, newText));
            }
        }
        return result;
    }

    @Tool(name = "bash", description = "Run a shell command in the workspace, e.g. {\"command\":\"ls -R\"}. Send the command as a single string (NOT a JSON array). Returns its exit code plus combined stdout/stderr. Use it to run 'sh verify.sh solution' / 'sh verify.sh template', inspect the project, and debug. Long output is truncated to the LAST 10000 characters (build failures and the verify.sh HYPERION_COLLECTED line are at the end); the COMPLETE output is saved in the sandbox to /tmp/hyperion/bash-<n>.log, so read earlier parts with sed/grep/head/tail on that file. After a verify.sh run the test reports are collected under /opt/hyperion/reports/<solution|template>/ — grep them for exact test names and pass/fail. Prefer grep/sed here over re-reading whole files.")
    public String bash(
            @ToolParam(description = "the shell command to run, as ONE string (not a JSON array), e.g. 'sh verify.sh solution', 'ls -R', or 'grep -n sort tests/test/sorting/SortTest.java'") String command) {
        return delegate.bash(command);
    }

    @Tool(name = "verify", description = "Run the authoritative self-check: builds the solution and the template, parses the test reports with the SAME production parser the final grader uses, and returns which tests pass/fail on each, the EXACT test names to bind your [task]s to (copy them verbatim — never guess), any template tests that wrongly pass, and a VERDICT. This is your primary self-check — call it after changes and iterate until the VERDICT says ACCEPTED before you submit. Each call re-runs both builds (no cache); it takes the same time as one 'sh verify.sh solution' plus one 'sh verify.sh template'.")
    public String verify() {
        return delegate.verify();
    }

    @Tool(name = "submit", description = "Submit the finished exercise for authoritative verification and end the session. Only call this after the 'verify' tool's VERDICT says ACCEPTED. Stop immediately after calling it.")
    public String submit(@ToolParam(required = false, description = "one-line summary of what you created or changed") String summary) {
        return delegate.submit(summary);
    }

    /**
     * Reconstructs the whole post-edit content without a sandbox read on the common path: the decorator already streamed the file's previous content, so it applies the same single
     * replacement the inner {@code edit_file} just applied (which guaranteed {@code oldText} is present and unique). Only if it holds no cached copy (e.g. the agent created the file
     * with {@code bash}) does it fall back to reading the file back through the delegate.
     */
    private String reconstructEditedContent(String path, String safe, String oldText, String newText) {
        String previous = latestContentByPath.get(safe);
        if (previous != null) {
            int index = previous.indexOf(oldText);
            if (index >= 0 && previous.indexOf(oldText, index + 1) < 0) {
                return previous.substring(0, index) + newText + previous.substring(index + oldText.length());
            }
        }
        // Cache miss or an unexpected mismatch: read the authoritative post-edit content back once so the streamed snapshot still reflects the sandbox truth.
        return delegate.readFile(path);
    }

    /**
     * Streams a whole-file snapshot to the sink, coalescing an identical consecutive write for the same path (no content change), and updates the per-path cache. Never lets a sink
     * failure disturb the agent run — streaming is best-effort UX.
     */
    private void emit(String safe, String action, String content) {
        if (content.equals(latestContentByPath.get(safe))) {
            return;
        }
        latestContentByPath.put(safe, content);
        try {
            snapshotSink.accept(HyperionFileSnapshotDTO.of(safe, action, content, currentTurn));
        }
        catch (RuntimeException e) {
            log.warn("Failed to stream file snapshot for '{}': {}", safe, e.getMessage());
        }
    }

    private static boolean isSuccess(@Nullable String result) {
        return result != null && result.startsWith(WRITE_SUCCESS_PREFIX);
    }
}
