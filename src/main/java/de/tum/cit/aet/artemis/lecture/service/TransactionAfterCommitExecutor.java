package de.tum.cit.aet.artemis.lecture.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;

@Conditional(LectureEnabled.class)
@Lazy
@Service
public class TransactionAfterCommitExecutor {

    /**
     * Executes the action after the current transaction commits. If no transaction synchronization is active, the action is executed immediately.
     *
     * @param action the action to execute
     */
    public void execute(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
