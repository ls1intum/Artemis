package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;

/** Decorates sandbox tools with lightweight file-change notifications for the instructor UI. */
public class FileChangeEmittingAgentTools implements TurnAware, SubmitVetoAware {

    private static final Logger log = LoggerFactory.getLogger(FileChangeEmittingAgentTools.class);

    private static final String WRITE_SUCCESS_PREFIX = "Wrote ";

    private static final String EDIT_SUCCESS_PREFIX = "Replaced ";

    private static final String DELETE_SUCCESS_PREFIX = "Deleted ";

    private final SandboxAgentTools delegate;

    private final Consumer<ExerciseGenerationFileChangeDTO> changeSink;

    /** The run's activity tracker, so a successful file mutation is counted exactly once, in the one place that already knows a write succeeded. */
    @Nullable
    private final GenerationActivityTracker activityTracker;

    private int currentTurn;

    public FileChangeEmittingAgentTools(SandboxAgentTools delegate, Consumer<ExerciseGenerationFileChangeDTO> changeSink) {
        this(delegate, changeSink, null);
    }

    public FileChangeEmittingAgentTools(SandboxAgentTools delegate, Consumer<ExerciseGenerationFileChangeDTO> changeSink, @Nullable GenerationActivityTracker activityTracker) {
        this.delegate = delegate;
        this.changeSink = changeSink;
        this.activityTracker = activityTracker;
    }

    @Override
    public void onTurn(int turn) {
        currentTurn = turn;
    }

    @Tool(name = "read_file", description = AgentToolDescriptions.READ_FILE)
    public String readFile(@ToolParam(description = AgentToolDescriptions.READ_FILE_PATH) String path,
            @ToolParam(required = false, description = AgentToolDescriptions.READ_FILE_OFFSET) Integer offset,
            @ToolParam(required = false, description = AgentToolDescriptions.READ_FILE_LIMIT) Integer limit) {
        return delegate.readFile(path, offset, limit);
    }

    @Tool(name = "search", description = AgentToolDescriptions.SEARCH)
    public String search(@ToolParam(description = AgentToolDescriptions.SEARCH_PATH) String path, @ToolParam(description = AgentToolDescriptions.SEARCH_QUERY) String query) {
        return delegate.search(path, query);
    }

    @Tool(name = "write_file", description = AgentToolDescriptions.WRITE_FILE)
    public String writeFile(@ToolParam(description = AgentToolDescriptions.WRITE_FILE_PATH) String path,
            @ToolParam(description = AgentToolDescriptions.WRITE_FILE_CONTENT) String content) {
        return emitOnSuccess(delegate.writeFile(path, content), WRITE_SUCCESS_PREFIX, path, ExerciseGenerationFileChangeDTO.ACTION_WRITE);
    }

    @Tool(name = "edit_file", description = AgentToolDescriptions.EDIT_FILE)
    public String editFile(@ToolParam(description = AgentToolDescriptions.EDIT_FILE_PATH) String path,
            @ToolParam(description = AgentToolDescriptions.EDIT_FILE_OLD_TEXT) String oldText, @ToolParam(description = AgentToolDescriptions.EDIT_FILE_NEW_TEXT) String newText) {
        return emitOnSuccess(delegate.editFile(path, oldText, newText), EDIT_SUCCESS_PREFIX, path, ExerciseGenerationFileChangeDTO.ACTION_EDIT);
    }

    @Tool(name = "delete_file", description = AgentToolDescriptions.DELETE_FILE)
    public String deleteFile(@ToolParam(description = AgentToolDescriptions.DELETE_FILE_PATH) String path) {
        return emitOnSuccess(delegate.deleteFile(path), DELETE_SUCCESS_PREFIX, path, ExerciseGenerationFileChangeDTO.ACTION_DELETE);
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

    @Override
    public boolean isSandboxSessionTerminated() {
        return delegate.isSandboxSessionTerminated();
    }

    @Override
    public boolean consumeSubmitVeto() {
        return delegate.consumeSubmitVeto();
    }

    private String emitOnSuccess(@Nullable String result, String successPrefix, String path, String action) {
        if (result != null && result.startsWith(successPrefix)) {
            if (activityTracker != null) {
                activityTracker.recordFileWritten();
            }
            String safePath = SandboxAgentTools.workspaceRelativePath(path);
            if (safePath != null) {
                emit(safePath, action);
            }
        }
        return result;
    }

    private void emit(String path, String action) {
        try {
            changeSink.accept(ExerciseGenerationFileChangeDTO.of(path, action, currentTurn));
        }
        catch (RuntimeException e) {
            log.warn("Failed to stream file change for '{}': {}", path, e.getMessage());
        }
    }
}
