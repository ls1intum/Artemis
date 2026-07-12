package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * Specification for creating an interactive sandbox session. The image is the same per-language execution image LocalCI uses for the exercise, resolved on the core node; the
 * container's CPU/memory/PID limits come from the build agent's host configuration. {@code runConfig} contributes only an optional network mode; interactive generation accepts
 * no network or Docker's {@code none} network only.
 *
 * @param image     the Docker image to start the warm container from
 * @param runConfig optional per-container overrides; only the network mode is consulted for Hyperion sandboxes
 * @param context   parent generation metadata required for observable relayed sessions
 */
public record SandboxSessionSpec(String image, DockerRunConfig runConfig, SandboxSessionContext context) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public SandboxSessionSpec(String image, DockerRunConfig runConfig) {
        this(image, runConfig, null);
    }
}
