package de.tum.cit.aet.artemis.buildagent.service.runner;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;

/**
 * Raw output of an isolated build. Closing this object releases the underlying execution resource.
 */
public final class BuildJobRunnerResult implements AutoCloseable {

    private final @Nullable InputStream resultArchive;

    private final int exitCode;

    private final ZonedDateTime completedAt;

    private final Runnable cleanup;

    private final AtomicBoolean closed = new AtomicBoolean();

    public BuildJobRunnerResult(@Nullable InputStream resultArchive, int exitCode, ZonedDateTime completedAt, Runnable cleanup) {
        this.resultArchive = resultArchive;
        this.exitCode = exitCode;
        this.completedAt = completedAt;
        this.cleanup = cleanup;
    }

    public @Nullable InputStream resultArchive() {
        return resultArchive;
    }

    public int exitCode() {
        return exitCode;
    }

    public ZonedDateTime completedAt() {
        return completedAt;
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        IOException closeFailure = null;
        if (resultArchive != null) {
            try {
                resultArchive.close();
            }
            catch (IOException e) {
                closeFailure = e;
            }
        }
        cleanup.run();
        if (closeFailure != null) {
            throw closeFailure;
        }
    }
}
