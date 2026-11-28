package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class BuildResultPollingSupportTest {

    private static final String COMMIT_HASH = "build-commit";

    private static final String FAILURE_MESSAGE = "Exception while polling for build result for commit {}: {}. Continuing...";

    @BeforeEach
    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void interruptionRestoresFlagAndRethrowsSameInstanceWithoutWarning() {
        Logger logger = mock(Logger.class);
        var interruption = new InterruptedException("Polling cancelled");
        try {
            assertThatThrownBy(() -> BuildResultPollingSupport.handleFailure(interruption, COMMIT_HASH, 0, logger)).isSameAs(interruption);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(logger);
        }
        finally {
            Thread.interrupted();
        }
    }

    @Test
    void ordinaryFailureWarnsOnTheCallerLoggerAndAcceptsZeroBackoff() {
        Logger logger = mock(Logger.class);
        var failure = new IllegalStateException("Database unavailable");
        assertThatCode(() -> BuildResultPollingSupport.handleFailure(failure, COMMIT_HASH, 0, logger)).doesNotThrowAnyException();
        verify(logger).warn(FAILURE_MESSAGE, COMMIT_HASH, failure.getMessage());
        verifyNoMoreInteractions(logger);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void interruptedFailureBackoffPropagatesSleepInterruptionWithItsFlagCleared() {
        Logger logger = mock(Logger.class);
        var failure = new IllegalStateException("Database unavailable");
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> BuildResultPollingSupport.handleFailure(failure, COMMIT_HASH, 0, logger)).isInstanceOf(InterruptedException.class);
            // Keep the original catch-block semantics: Thread.sleep clears the flag, and no sibling catch restores it.
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
            verify(logger).warn(FAILURE_MESSAGE, COMMIT_HASH, failure.getMessage());
            verifyNoMoreInteractions(logger);
        }
        finally {
            Thread.interrupted();
        }
    }
}
