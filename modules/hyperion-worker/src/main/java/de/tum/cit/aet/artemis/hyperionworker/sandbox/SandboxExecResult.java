package de.tum.cit.aet.artemis.hyperionworker.sandbox;

import java.io.Serial;
import java.io.Serializable;

/** Command result with bounded stdout/stderr. A timeout removes the session; callers must not reuse it. */
public record SandboxExecResult(int exitCode, String stdout, String stderr, boolean timedOut) implements Serializable {

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
