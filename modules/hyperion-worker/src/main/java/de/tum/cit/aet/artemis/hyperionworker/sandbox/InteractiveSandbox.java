package de.tum.cit.aet.artemis.hyperionworker.sandbox;

import java.io.InputStream;
import java.time.Duration;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * A reusable container workspace for command execution and file transfer. All operations are local to the worker supervisor; this interface is never exposed over messaging.
 * The caller must treat the returned session handle as opaque. Commands execute directly; pass {@code sh -c} explicitly when shell interpretation is needed.
 * Output is bounded, and destruction is idempotent.
 */
public interface InteractiveSandbox {

    String createSession();

    SandboxExecResult exec(String sessionId, Duration timeout, String... command);

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
