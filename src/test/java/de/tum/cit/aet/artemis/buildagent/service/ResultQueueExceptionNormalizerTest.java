package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.errors.TransportException;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataTypeFilter;
import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataTypeFilter.FilteringClassLoader;

class ResultQueueExceptionNormalizerTest {

    private static final DistributedDataTypeFilter FILTER = DistributedDataTypeFilter.withAdditional(null);

    /**
     * Reads a value the way the distributed data providers do: every class name is resolved through the filter.
     */
    private static Throwable roundTrip(Throwable throwable) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(throwable);
        }
        var loader = new FilteringClassLoader(ResultQueueExceptionNormalizerTest.class.getClassLoader(), FILTER);
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {

            @Override
            protected Class<?> resolveClass(ObjectStreamClass description) throws IOException, ClassNotFoundException {
                return Class.forName(description.getName(), false, loader);
            }
        }) {
            return (Throwable) in.readObject();
        }
    }

    @Test
    void aFailureOfTheGitClientCanBeReadFromTheQueue() throws Exception {
        var failure = new RuntimeException("Error while cloning", new TransportException("https://example.com/git/ABC/abc-exercise.git: not authorized"));

        var normalized = ResultQueueExceptionNormalizer.normalize(failure);

        var read = roundTrip(normalized);
        assertThat(read).hasMessage("Error while cloning");
        assertThat(read.getCause()).hasMessageContaining("not authorized").hasMessageContaining(TransportException.class.getName());
        assertThat(read.getStackTrace()).isEqualTo(failure.getStackTrace());
    }

    @Test
    void theExceptionOfTheOriginalChainIsNotReadableWithoutNormalization() {
        var failure = new RuntimeException("Error while cloning", new TransportException("not authorized"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> roundTrip(failure)).hasMessageContaining(TransportException.class.getName());
    }

    @Test
    void aStorableChainIsNotReplaced() {
        var failure = new IllegalStateException("outer", new IOException("inner"));

        assertThat(ResultQueueExceptionNormalizer.normalize(failure)).isSameAs(failure);
        assertThat(ResultQueueExceptionNormalizer.normalize(null)).isNull();
    }

    @Test
    void theCauseAndTheMessageThatTheCoreChecksAreKept() throws Exception {
        var cancelled = new RuntimeException("Build job with id 1 was cancelled.", new CancellationException("cancelled"));
        cancelled.addSuppressed(new TransportException("suppressed"));
        var timedOut = new RuntimeException("Build job with id 2 was timed out", new TimeoutException("timeout"));
        timedOut.addSuppressed(new TransportException("suppressed"));

        var readCancelled = roundTrip(ResultQueueExceptionNormalizer.normalize(cancelled));
        var readTimedOut = roundTrip(ResultQueueExceptionNormalizer.normalize(timedOut));

        assertThat(readCancelled).hasMessage("Build job with id 1 was cancelled.");
        assertThat(readCancelled.getCause()).isInstanceOf(CancellationException.class);
        assertThat(readTimedOut).hasMessage("Build job with id 2 was timed out");
        assertThat(readTimedOut.getCause()).isInstanceOf(TimeoutException.class);
    }

    @Test
    void aCauseThatIsOfAnotherTypeBelowACancellationIsStillReadable() throws Exception {
        var failure = new RuntimeException("Build job with id 3 was cancelled.", new CancellationException("cancelled"));
        failure.getCause().initCause(new TransportException("below the cancellation"));

        var read = roundTrip(ResultQueueExceptionNormalizer.normalize(failure));

        assertThat(read.getCause()).isInstanceOf(CancellationException.class);
        assertThat(read.getCause().getCause()).hasMessageContaining("below the cancellation");
    }
}
