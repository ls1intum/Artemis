package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileSnapshotDTO;

/**
 * A thin decorator around {@link SandboxAgentTools} that re-exposes the same {@code @Tool} surface but, whenever {@code write_file} or {@code edit_file} succeeds, emits a
 * whole-file {@link ExerciseGenerationFileSnapshotDTO} to the given sink for live streaming to the triggering instructor.
 * <p>
 * It never touches the tool bodies (read/bash/verify/submit are pure delegations, and the write paths emit only on success). The whole file content is already in memory:
 * {@code write_file} receives it as an argument, and {@code edit_file} reconstructs it from the last snapshot for that path (falling back to a single read only on a cache miss),
 * so
 * no extra sandbox read-back is needed on the common path.
 * <p>
 * Created per session and driven serially by the single-threaded agent loop, so the per-path cache needs no synchronisation.
 */
public class FileSnapshotEmittingAgentTools implements TurnAware {

    private static final Logger log = LoggerFactory.getLogger(FileSnapshotEmittingAgentTools.class);

    /** A successful {@code write_file}/{@code edit_file} result starts with this marker (see {@link SandboxAgentTools#writeFile}); an error starts with {@code ERROR}. */
    private static final String WRITE_SUCCESS_PREFIX = "Wrote ";

    /**
     * A failed delegate read is reported as an {@code "ERROR: ..."} sentinel (see {@link SandboxAgentTools#readFile}); such a string must never be cached or streamed as content.
     */
    private static final String READ_ERROR_PREFIX = "ERROR";

    private final SandboxAgentTools delegate;

    private final Consumer<ExerciseGenerationFileSnapshotDTO> snapshotSink;

    /** The last full content streamed per (normalised) path, so an {@code edit_file} can rebuild the whole file without a sandbox read and identical writes can be coalesced. */
    private final Map<String, String> latestContentByPath = new LinkedHashMap<>();

    private int currentTurn = 0;

    /**
     * @param delegate     the underlying tools that actually operate on the sandbox
     * @param snapshotSink receives a whole-file snapshot on every successful write; must not be {@code null}
     */
    public FileSnapshotEmittingAgentTools(SandboxAgentTools delegate, Consumer<ExerciseGenerationFileSnapshotDTO> snapshotSink) {
        this.delegate = delegate;
        this.snapshotSink = snapshotSink;
    }

    @Override
    public void onTurn(int turn) {
        this.currentTurn = turn;
    }

    @Tool(name = "read_file", description = AgentToolDescriptions.READ_FILE)
    public String readFile(@ToolParam(description = AgentToolDescriptions.READ_FILE_PATH) String path) {
        return delegate.readFile(path);
    }

    /**
     * Delegates to the underlying sandbox write, then emits a {@code FILE_SNAPSHOT} of the whole file so the editor can follow along live.
     *
     * @param path    the workspace-relative path to write
     * @param content the complete new file content
     * @return a confirmation, or an actionable error message
     */
    @Tool(name = "write_file", description = AgentToolDescriptions.WRITE_FILE)
    public String writeFile(@ToolParam(description = AgentToolDescriptions.WRITE_FILE_PATH) String path,
            @ToolParam(description = AgentToolDescriptions.WRITE_FILE_CONTENT) String content) {
        String result = delegate.writeFile(path, content);
        if (isSuccess(result)) {
            String safe = SandboxAgentTools.workspaceRelativePath(path);
            if (safe != null) {
                // create vs edit is decided from this run's own write cache, so the first write of a path in an ADAPT run (where the file already exists on disk) is labelled
                // create.
                // This is a cosmetic live-preview label only — never persisted and irrelevant to correctness — so it is not worth a per-write sandbox stat to disambiguate.
                ExerciseGenerationFileSnapshotDTO.Action action = latestContentByPath.containsKey(safe) ? ExerciseGenerationFileSnapshotDTO.Action.EDIT
                        : ExerciseGenerationFileSnapshotDTO.Action.CREATE;
                emit(safe, action, content);
            }
        }
        return result;
    }

    /**
     * Delegates to the underlying sandbox edit, then emits a {@code FILE_SNAPSHOT} of the reconstructed whole file so the editor can follow along live.
     *
     * @param path    the workspace-relative path to edit
     * @param oldText the exact existing text to replace, byte-for-byte; must be unique in the file
     * @param newText the replacement text
     * @return a confirmation, or an actionable error message if the match is missing or ambiguous
     */
    @Tool(name = "edit_file", description = AgentToolDescriptions.EDIT_FILE)
    public String editFile(@ToolParam(description = AgentToolDescriptions.EDIT_FILE_PATH) String path,
            @ToolParam(description = AgentToolDescriptions.EDIT_FILE_OLD_TEXT) String oldText, @ToolParam(description = AgentToolDescriptions.EDIT_FILE_NEW_TEXT) String newText) {
        String result = delegate.editFile(path, oldText, newText);
        if (isSuccess(result)) {
            String safe = SandboxAgentTools.workspaceRelativePath(path);
            if (safe != null) {
                String content = reconstructEditedContent(path, safe, oldText, newText);
                // Skip the snapshot when the fallback read failed: a delegate read can return an "ERROR: ..." sentinel, which must never be cached or streamed as file content.
                if (content != null) {
                    emit(safe, ExerciseGenerationFileSnapshotDTO.Action.EDIT, content);
                }
            }
        }
        return result;
    }

    @Tool(name = "bash", description = AgentToolDescriptions.BASH)
    public String bash(@ToolParam(description = AgentToolDescriptions.BASH_COMMAND) String command) {
        return delegate.bash(command);
    }

    @Tool(name = "verify", description = AgentToolDescriptions.VERIFY)
    public String verify() {
        return delegate.verify();
    }

    @Tool(name = "submit", description = AgentToolDescriptions.SUBMIT)
    public String submit(@ToolParam(required = false, description = AgentToolDescriptions.SUBMIT_SUMMARY) String summary) {
        return delegate.submit(summary);
    }

    /**
     * Reconstructs the whole post-edit content without a sandbox read on the common path: the decorator already streamed the file's previous content, so it applies the same single
     * replacement the inner {@code edit_file} just applied (which guaranteed {@code oldText} is present and unique). Only if it holds no cached copy (e.g. the agent created the
     * file
     * with {@code bash}) does it fall back to reading the file back through the delegate.
     */
    @Nullable
    private String reconstructEditedContent(String path, String safe, String oldText, String newText) {
        String previous = latestContentByPath.get(safe);
        if (previous != null) {
            int index = previous.indexOf(oldText);
            if (index >= 0 && previous.indexOf(oldText, index + 1) < 0) {
                return previous.substring(0, index) + newText + previous.substring(index + oldText.length());
            }
        }
        // Cache miss or an unexpected mismatch: read the authoritative post-edit content back once so the streamed snapshot still reflects the sandbox truth.
        String readBack = delegate.readFile(path);
        // delegate.readFile returns an "ERROR: ..." sentinel (see SandboxAgentTools#readFile) on failure; that is not file content, so surface null and let the caller skip the
        // snapshot.
        if (readBack == null || readBack.startsWith(READ_ERROR_PREFIX)) {
            return null;
        }
        return readBack;
    }

    /**
     * Streams a whole-file snapshot to the sink, coalescing an identical consecutive write for the same path (no content change), and updates the per-path cache. Never lets a sink
     * failure disturb the agent run — streaming is best-effort UX.
     */
    private void emit(String safe, ExerciseGenerationFileSnapshotDTO.Action action, String content) {
        if (content.equals(latestContentByPath.get(safe))) {
            return;
        }
        latestContentByPath.put(safe, content);
        try {
            snapshotSink.accept(ExerciseGenerationFileSnapshotDTO.of(safe, action, content, currentTurn));
        }
        catch (RuntimeException e) {
            log.warn("Failed to stream file snapshot for '{}': {}", safe, e.getMessage());
        }
    }

    private static boolean isSuccess(@Nullable String result) {
        return result != null && result.startsWith(WRITE_SUCCESS_PREFIX);
    }
}
