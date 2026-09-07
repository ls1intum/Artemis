package de.tum.cit.aet.artemis.buildagent.service;

import java.io.InputStream;
import java.time.Duration;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpecDTO;

/**
 * A long-lived interactive execution sandbox: a warm Docker container a caller drives through many cheap operations (read a file, write a file, run a command), rather than the
 * fire-and-forget single-script model of a regular CI build. The container stays warm across the session, so the toolchain resolves once and incremental builds reuse it.
 * <p>
 * This decouples the agent loop (on the core node, holding the LLM client and database) from code execution (on a build agent, where untrusted code runs in isolation without
 * credentials or database access). Production core nodes relay operations to the owning build agent, including when core and build agent run in the same process.
 * <p>
 * The opaque session handle returned by {@link #createSession} identifies the session in every later call. {@link #exec} runs its command directly rather than
 * through a shell (pass {@code sh -c ...} to get one) and truncates captured output to a bounded size. {@link #destroySession} is safe to call more than once.
 */
public interface InteractiveSandbox {

    String createSession(SandboxSessionSpecDTO spec);

    SandboxExecResultDTO exec(String sessionId, Duration timeout, String... command);

    void copyIn(String sessionId, String destinationPath, InputStream tarArchive);

    TarArchiveInputStream copyOut(String sessionId, String path);

    /**
     * Restarts the container, discarding its writable tmpfs mounts and killing every process started by prior commands.
     *
     * @param sessionId the session handle
     */
    default void resetSession(String sessionId) {
        throw new UnsupportedOperationException("This interactive sandbox does not support session reset");
    }

    void destroySession(String sessionId);
}
