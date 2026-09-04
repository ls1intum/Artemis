package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * Result of a single command execution inside an interactive sandbox container. Unlike the regular build-job path, an agentic session needs the captured stdout/stderr back as the
 * observation for its next reasoning step; output is truncated by the producing service so large build logs cannot blow up the agent's context window.
 *
 * @param exitCode the process exit code ({@code 0} on success)
 * @param stdout   the captured standard output (already truncated)
 * @param stderr   the captured standard error (already truncated)
 * @param timedOut whether the command was killed because it exceeded its timeout
 */
public record SandboxExecResultDTO(int exitCode, String stdout, String stderr, boolean timedOut) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

    /**
     * Merges stdout and stderr into the single observation form the agent reasons over.
     *
     * @return stdout and stderr joined (whichever are non-empty), or an empty string when both are empty
     */
    public String combinedOutput() {
        if (stderr == null || stderr.isEmpty()) {
            return stdout == null ? "" : stdout;
        }
        if (stdout == null || stdout.isEmpty()) {
            return stderr;
        }
        return stdout + "\n" + stderr;
    }
}
