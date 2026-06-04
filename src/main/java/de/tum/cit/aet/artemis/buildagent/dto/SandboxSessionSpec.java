package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;

/**
 * Specification for creating an interactive sandbox session.
 * <p>
 * The image is the same per-language execution image LocalCI already uses for the exercise, resolved on the core node. The session container's CPU/memory/PID limits come from
 * the build agent's host configuration; {@code runConfig} currently only contributes an optional network mode (when {@code null}, the container uses the build agent's default
 * network, matching a normal CI build so build dependencies resolve).
 *
 * @param image     the Docker image to start the warm container from
 * @param runConfig optional per-container overrides; only the network mode is consulted for generation sessions
 */
public record SandboxSessionSpec(String image, DockerRunConfig runConfig) implements Serializable {
}
