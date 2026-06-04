package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;

/**
 * Result of a single command execution inside an interactive sandbox container.
 * <p>
 * Unlike the regular build-job execution path (which streams output into the build-log map and only returns an exit code), an agentic generation session needs the captured
 * standard output and error back as the observation for the next reasoning step. Output is truncated by the producing service before it is placed here so that large build logs
 * cannot blow up the agent's context window.
 *
 * @param exitCode the process exit code ({@code 0} on success)
 * @param stdout   the captured standard output (already truncated)
 * @param stderr   the captured standard error (already truncated)
 * @param timedOut whether the command was killed because it exceeded its timeout
 */
public record SandboxExecResult(int exitCode, String stdout, String stderr, boolean timedOut) implements Serializable {

    /**
     * @return {@code true} if the command finished with a zero exit code and did not time out
     */
    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

    /**
     * @return the merged standard output and error, the form most useful as a single observation for the agent
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
