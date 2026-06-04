package de.tum.cit.aet.artemis.buildagent.service;

import java.io.InputStream;
import java.time.Duration;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;

/**
 * A long-lived, hardened, interactive execution sandbox: a warm Docker container that an agentic exercise-generation session drives through many cheap operations
 * (read a file, write a file, run a command), rather than the fire-and-forget single-script model of a regular CI build.
 * <p>
 * This is the generic primitive that decouples the agent loop (which runs on the core node, holding the LLM client and database) from the actual code execution (which must
 * happen on a build agent, where untrusted code already runs in isolation without access to credentials or the database). A single-node deployment talks to a local
 * implementation in-process; a multi-node deployment relays the same operations to the owning build agent.
 * <p>
 * The fast feedback loop comes from the container staying <em>warm</em> across the whole session: dependencies and the toolchain are resolved once, files are edited in place,
 * and incremental builds reuse the warm state — there is no per-iteration container start or cold build.
 */
public interface InteractiveSandbox {

    /**
     * Creates and starts a warm, hardened container for a generation session and returns an opaque session handle (the container identifier).
     *
     * @param spec the image and resource/network configuration for the session
     * @return the session handle (container id) to pass to subsequent operations
     */
    String createSession(SandboxSessionSpec spec);

    /**
     * Runs a command inside the session container and returns the captured output and exit code. Standard output and error are truncated to a bounded size before being
     * returned so that large build logs cannot overflow the agent's context window.
     *
     * @param sessionId the session handle returned by {@link #createSession}
     * @param command   the command and its arguments (executed without a shell unless the caller passes {@code sh -c ...})
     * @param timeout   the maximum time to wait for the command to finish before it is killed
     * @return the captured result
     */
    SandboxExecResult exec(String sessionId, Duration timeout, String... command);

    /**
     * Copies a tar archive into the session container at the given destination path. Used to seed the workspace (problem statement and all repositories) at the start of a
     * session and to write individual generated files.
     *
     * @param sessionId       the session handle
     * @param destinationPath the absolute path inside the container to extract the archive into
     * @param tarArchive      the tar archive stream
     */
    void copyIn(String sessionId, String destinationPath, InputStream tarArchive);

    /**
     * Reads a path out of the session container as a tar archive. Used to extract the produced files at the end of a session.
     *
     * @param sessionId the session handle
     * @param path      the absolute path inside the container to read
     * @return the tar archive stream of the path
     */
    TarArchiveInputStream copyOut(String sessionId, String path);

    /**
     * Stops and removes the session container, releasing its resources. Safe to call more than once.
     *
     * @param sessionId the session handle
     */
    void destroySession(String sessionId);
}
