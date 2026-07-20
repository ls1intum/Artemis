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

    private static final String DELETE_SUCCESS_PREFIX = "Deleted ";

    private final SandboxAgentTools delegate;

    private final Consumer<ExerciseGenerationFileChangeDTO> changeSink;

    private int currentTurn;

    public FileChangeEmittingAgentTools(SandboxAgentTools delegate, Consumer<ExerciseGenerationFileChangeDTO> changeSink) {
        this.delegate = delegate;
        this.changeSink = changeSink;
    }

    @Override
    public void onTurn(int turn) {
        currentTurn = turn;
    }

    @Tool(name = "read_file", description = AgentToolDescriptions.READ_FILE)
    public String readFile(@ToolParam(description = AgentToolDescriptions.READ_FILE_PATH) String path) {
        return delegate.readFile(path);
    }

    /**
     * Writes the file, delegating to {@link SandboxAgentTools#writeFile}, and emits a file-change notification when the write succeeds.
     *
     * @param path    the workspace-relative file path to write
     * @param content the complete new file content
     * @return a confirmation, or an actionable error message
     */
    @Tool(name = "write_file", description = AgentToolDescriptions.WRITE_FILE)
    public String writeFile(@ToolParam(description = AgentToolDescriptions.WRITE_FILE_PATH) String path,
            @ToolParam(description = AgentToolDescriptions.WRITE_FILE_CONTENT) String content) {
        String result = delegate.writeFile(path, content);
        if (isSuccess(result)) {
            String safePath = SandboxAgentTools.workspaceRelativePath(path);
            if (safePath != null) {
                emit(safePath, ExerciseGenerationFileChangeDTO.ACTION_WRITE);
            }
        }
        return result;
    }

    /**
     * Edits the file, delegating to {@link SandboxAgentTools#editFile}, and emits a file-change notification when the edit succeeds.
     *
     * @param path    the workspace-relative file path to edit
     * @param oldText the exact text to replace
     * @param newText the replacement text
     * @return a confirmation, or an actionable error message if the match is missing or ambiguous
     */
    @Tool(name = "edit_file", description = AgentToolDescriptions.EDIT_FILE)
    public String editFile(@ToolParam(description = AgentToolDescriptions.EDIT_FILE_PATH) String path,
            @ToolParam(description = AgentToolDescriptions.EDIT_FILE_OLD_TEXT) String oldText, @ToolParam(description = AgentToolDescriptions.EDIT_FILE_NEW_TEXT) String newText) {
        String result = delegate.editFile(path, oldText, newText);
        if (isSuccess(result)) {
            String safePath = SandboxAgentTools.workspaceRelativePath(path);
            if (safePath != null) {
                emit(safePath, ExerciseGenerationFileChangeDTO.ACTION_EDIT);
            }
        }
        return result;
    }

    /**
     * Deletes the file, delegating to {@link SandboxAgentTools#deleteFile}, and emits a file-change notification when the delete succeeds.
     *
     * @param path the workspace-relative file path to delete
     * @return a confirmation, or an actionable error message
     */
    @Tool(name = "delete_file", description = AgentToolDescriptions.DELETE_FILE)
    public String deleteFile(@ToolParam(description = AgentToolDescriptions.DELETE_FILE_PATH) String path) {
        String result = delegate.deleteFile(path);
        if (result != null && result.startsWith(DELETE_SUCCESS_PREFIX)) {
            String safePath = SandboxAgentTools.workspaceRelativePath(path);
            if (safePath != null) {
                emit(safePath, ExerciseGenerationFileChangeDTO.ACTION_DELETE);
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

    boolean isSandboxSessionTerminated() {
        return delegate.isSandboxSessionTerminated();
    }

    @Override
    public boolean consumeSubmitVeto() {
        return delegate.consumeSubmitVeto();
    }

    private void emit(String path, String action) {
        try {
            changeSink.accept(ExerciseGenerationFileChangeDTO.of(path, action, currentTurn));
        }
        catch (RuntimeException e) {
            log.warn("Failed to stream file change for '{}': {}", path, e.getMessage());
        }
    }

    private static boolean isSuccess(@Nullable String result) {
        return result != null && result.startsWith(WRITE_SUCCESS_PREFIX);
    }
}
