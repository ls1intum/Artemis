package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionAfterCommitExecutorTest {

    private final TransactionAfterCommitExecutor executor = new TransactionAfterCommitExecutor();

    @Test
    void executesImmediatelyWithoutTransactionSynchronization() {
        var executed = new AtomicBoolean();

        executor.execute(() -> executed.set(true));

        assertThat(executed).isTrue();
    }

    @Test
    void defersExecutionUntilCommit() {
        var executed = new AtomicBoolean();
        TransactionSynchronizationManager.initSynchronization();
        try {
            executor.execute(() -> executed.set(true));

            assertThat(executed).isFalse();
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertThat(executed).isTrue();
        }
        finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void doesNotExecuteAfterRollback() {
        var executed = new AtomicBoolean();
        TransactionSynchronizationManager.initSynchronization();
        try {
            executor.execute(() -> executed.set(true));

            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(executed).isFalse();
        }
        finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
