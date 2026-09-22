package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Path;

import org.eclipse.jgit.api.errors.CanceledException;
import org.junit.jupiter.api.Test;

class RepositoryCheckoutLocksTest {

    @Test
    void cancelledAcquisitionDoesNotPoisonTheNextRepositoryOnTheBatchThread() throws Exception {
        var locks = new RepositoryCheckoutLocks();
        try {
            Thread.currentThread().interrupt();
            assertThatExceptionOfType(CanceledException.class).isThrownBy(() -> locks.acquire(Path.of("first"), 1));
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
            try (var ignored = locks.acquire(Path.of("second"), 1)) {
                assertThat(Thread.currentThread().isInterrupted()).isFalse();
            }
        }
        finally {
            Thread.interrupted();
        }
    }
}
