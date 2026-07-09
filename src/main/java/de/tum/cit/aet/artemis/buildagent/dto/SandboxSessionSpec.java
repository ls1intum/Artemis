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
 */
public record SandboxSessionSpec(String image, DockerRunConfig runConfig) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
