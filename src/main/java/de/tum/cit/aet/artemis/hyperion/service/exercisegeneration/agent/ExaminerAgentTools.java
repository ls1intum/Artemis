package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;

/**
 * The trimmed tool surface for the decorrelated test-author (independent examiner) agent: the same file/shell tools as {@link SandboxAgentTools} but without the {@code verify}
 * tool. {@code verify} runs the solution-aware differential (it builds the reference solution), and the examiner must not observe the solution's behaviour — its only ground truth
 * is the problem statement's stated contract.
 * <p>
 * Decorrelation is enforced primarily by absence (the examiner's container is seeded without {@code solution/}); this restricted toolset backs that up — no tool is wired to the
 * solution-aware verifier. The examiner iterates {@code bash sh verify.sh template} only to make its suite compile against the template's public API, never to make a test pass.
 */
public class ExaminerAgentTools {

    /** The file/shell tools via the verify-free constructor; the solution-aware {@code verify} tool is not re-exposed here. */
    private final SandboxAgentTools delegate;

    /**
     * @param sandbox   the examiner's own sandbox session (seeded without {@code solution/})
     * @param sessionId the examiner session handle
     */
    public ExaminerAgentTools(InteractiveSandbox sandbox, String sessionId) {
        this.delegate = new SandboxAgentTools(sandbox, sessionId);
    }

    /**
     * Reads a workspace file.
     *
     * @param path the workspace-relative path to read
     * @return the file content, or an actionable error message
     */
    @Tool(name = "read_file", description = AgentToolDescriptions.READ_FILE)
    public String readFile(@ToolParam(description = AgentToolDescriptions.READ_FILE_PATH) String path) {
        return delegate.readFile(path);
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
        return delegate.writeFile(path, content);
    }

    /**
     * Replaces a unique snippet in a workspace file.
     *
     * @param path    the workspace-relative path to edit
     * @param oldText the exact text to replace; must occur exactly once
     * @param newText the replacement text
     * @return a confirmation, or an actionable error message
     */
    @Tool(name = "edit_file", description = AgentToolDescriptions.EDIT_FILE)
    public String editFile(@ToolParam(description = AgentToolDescriptions.EDIT_FILE_PATH) String path,
            @ToolParam(description = AgentToolDescriptions.EDIT_FILE_OLD_TEXT) String oldText, @ToolParam(description = AgentToolDescriptions.EDIT_FILE_NEW_TEXT) String newText) {
        return delegate.editFile(path, oldText, newText);
    }

    /**
     * Runs a shell command in the workspace (the examiner uses {@code sh verify.sh template} to check its suite COMPILES against the template's public API).
     *
     * @param command the shell command to run, as a single string
     * @return the exit status followed by the combined stdout/stderr
     */
    @Tool(name = "bash", description = AgentToolDescriptions.BASH)
    public String bash(@ToolParam(description = AgentToolDescriptions.BASH_COMMAND) String command) {
        return delegate.bash(command);
    }

    /**
     * Signals the examiner suite is complete (compiles against the template and pins the stated contract); ends the examiner loop.
     *
     * @param summary an optional one-line summary of the authored suite
     * @return a confirmation that the suite was submitted
     */
    @Tool(name = "submit", description = AgentToolDescriptions.EXAMINER_SUBMIT)
    public String submit(@ToolParam(required = false, description = AgentToolDescriptions.SUBMIT_SUMMARY) String summary) {
        return delegate.submit(summary);
    }
}
