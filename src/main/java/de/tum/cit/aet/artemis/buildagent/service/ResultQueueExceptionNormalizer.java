package de.tum.cit.aet.artemis.buildagent.service;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataTypeFilter;

/**
 * Prepares the exception of a failed build job for the result queue.
 * <p>
 * The queue stores the exception with its whole chain of causes, and the distributed data providers only read the types of
 * {@link DistributedDataTypeFilter}. The cause of a failed build can be any type that a library throws (for example an exception of the Git client), so a chain that
 * contains a type outside the list is replaced by a chain of plain exceptions with the same messages and stack traces. The core only looks at whether the cause is a
 * {@link CancellationException} or a {@link TimeoutException}, at the message, and at the stack trace in the log, which stay as they are.
 */
final class ResultQueueExceptionNormalizer {

    private static final DistributedDataTypeFilter FILTER = DistributedDataTypeFilter.withAdditional(null);

    /**
     * Longest chain that is followed, so that a cyclic or extremely deep chain cannot keep the build agent busy.
     */
    private static final int MAX_DEPTH = 20;

    private ResultQueueExceptionNormalizer() {
    }

    /**
     * @param exception the exception of a failed build job, may be {@code null}
     * @return the exception itself if the queue can store it, otherwise a copy that only consists of types that the queue can store
     */
    static @Nullable Throwable normalize(@Nullable Throwable exception) {
        if (exception == null || isStorable(exception, 0)) {
            return exception;
        }
        return copy(exception, 0);
    }

    private static boolean isStorable(@Nullable Throwable throwable, int depth) {
        if (throwable == null) {
            return true;
        }
        if (depth >= MAX_DEPTH || !FILTER.isAllowed(throwable.getClass().getName()) || throwable.getSuppressed().length > 0) {
            return false;
        }
        return isStorable(throwable.getCause(), depth + 1);
    }

    private static @Nullable Throwable copy(@Nullable Throwable original, int depth) {
        if (original == null || depth >= MAX_DEPTH) {
            return null;
        }
        Throwable cause = copy(original.getCause(), depth + 1);
        Throwable copy;
        if (original instanceof CancellationException) {
            copy = new CancellationException(original.getMessage());
            copy.initCause(cause);
        }
        else if (original instanceof TimeoutException) {
            copy = new TimeoutException(original.getMessage());
            copy.initCause(cause);
        }
        else {
            // The message of the top level exception is kept as it is, because the core compares it with the message of a cancelled build
            copy = new RuntimeException(depth == 0 ? original.getMessage() : describe(original), cause);
        }
        copy.setStackTrace(original.getStackTrace());
        return copy;
    }

    private static String describe(Throwable throwable) {
        // The class name stays visible, because it is the only hint to the kind of failure once the type is replaced
        String message = throwable.getMessage();
        return message == null ? throwable.getClass().getName() : throwable.getClass().getName() + ": " + message;
    }
}
