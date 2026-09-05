package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Unit tests for {@link ProgrammingVariantAdapterService#awaitConsistencyTask}: it is called from a {@code finally}
 * block around Gate 1/2 in {@code verify()}, so it must NEVER itself throw — regardless of the future's outcome
 * — or it would suppress whatever Gate 1/2 exception is already propagating. Covers the regression this was
 * added to fix: the consistency check used to have no timeout at all, so a hung future (or one still running
 * because Gate 1 threw before ever joining it) would block the whole VERIFYING call — and, via the
 * try-with-resources' implicit {@code ExecutorService.close()}, the enclosing method — indefinitely.
 */
class ProgrammingVariantAdapterServiceAwaitConsistencyTaskTest {

    private final ProgrammingExercise exercise = mock(ProgrammingExercise.class);

    @Test
    void shouldCompleteSilentlyWhenTheFutureFinishesInTime() {
        Future<?> future = mock(Future.class);

        assertThatCode(() -> ProgrammingVariantAdapterService.awaitConsistencyTask(future, exercise)).doesNotThrowAnyException();
    }

    @Test
    void shouldCancelAndSwallowATimeoutInsteadOfBlockingForever() throws Exception {
        Future<?> future = mock(Future.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException());

        assertThatCode(() -> ProgrammingVariantAdapterService.awaitConsistencyTask(future, exercise)).doesNotThrowAnyException();

        verify(future, times(1)).cancel(true);
    }

    @Test
    void shouldSwallowAnExecutionExceptionRatherThanFailVerification() throws Exception {
        Future<?> future = mock(Future.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new ExecutionException(new RuntimeException("boom")));

        assertThatCode(() -> ProgrammingVariantAdapterService.awaitConsistencyTask(future, exercise)).doesNotThrowAnyException();

        verify(future, never()).cancel(true);
    }

    @Test
    void shouldRestoreTheInterruptFlagWithoutThrowing() throws Exception {
        Future<?> future = mock(Future.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());

        try {
            assertThatCode(() -> ProgrammingVariantAdapterService.awaitConsistencyTask(future, exercise)).doesNotThrowAnyException();
            if (!Thread.interrupted()) {
                throw new AssertionError("Expected the interrupt flag to be set after an InterruptedException");
            }
        }
        finally {
            // Clear defensively in case the assertion above threw before consuming the flag.
            Thread.interrupted();
        }
    }
}
