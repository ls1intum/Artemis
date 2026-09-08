package de.tum.cit.aet.artemis.exam.service;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;

/**
 * Serialises the operations that read an exam's exercise groups and then write a selection derived from them.
 * <p>
 * Student exam generation picks one exercise per exercise group and persists that selection. Moving an exercise into a
 * different group changes what those groups contain. Run concurrently, a generation can read the groups, a move can
 * commit, and the generation can then persist a selection that no longer holds one exercise per group — while the
 * move's own {@code NOT EXISTS} guard saw no committed student exam and therefore let the move through. Both sides take
 * this lock so that window does not exist.
 * <p>
 * <strong>Why a cluster mutex and not a database row lock.</strong> This replaces a pessimistic write lock on the exam
 * row held inside a {@code TransactionTemplate}. That construction serialised every student starting the exam behind
 * whichever mass generation was running, held database locks for the length of it, and put a transaction boundary in a
 * service — see {@code documentation/docs/developer/guidelines/performance.mdx}. The mutex serialises the same
 * operations without holding a single database lock. It is weaker in one respect, spelled out in {@link DistributedLock}:
 * none of the backends are consensus-backed, so a partition at the wrong moment can admit two holders. What survives
 * that case is the atomic guard inside the move's own statement, which is the same protection the code had before the
 * row lock was introduced.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamExerciseSelectionLockService {

    private static final String LOCK_NAME_PREFIX = "exam-exercise-selection-";

    /**
     * Generating student exams for every registered user of a large exam is the longest critical section here, and
     * students starting that same exam queue behind it. Generous enough to let a mass generation finish, short enough
     * that a lock leaked by a dying node does not block an exam indefinitely.
     */
    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(2);

    private final DistributedDataProvider distributedDataProvider;

    public ExamExerciseSelectionLockService(DistributedDataProvider distributedDataProvider) {
        this.distributedDataProvider = distributedDataProvider;
    }

    /**
     * Runs the given action while holding the exam's exercise selection lock.
     *
     * @param examId the id of the exam whose exercise selection the action reads or writes
     * @param action the action to run under the lock; must not perform slow or remote work
     * @param <T>    the type the action returns
     * @return whatever the action returned
     * @throws InternalServerErrorException if the lock cannot be acquired within {@link #LOCK_TIMEOUT}
     */
    public <T> T callUnderLock(long examId, Supplier<T> action) {
        DistributedLock lock = distributedDataProvider.getLock(LOCK_NAME_PREFIX + examId);
        if (!lock.tryLock(LOCK_TIMEOUT)) {
            throw new InternalServerErrorException("Could not acquire the exercise selection lock for exam " + examId + " within " + LOCK_TIMEOUT.toSeconds() + " seconds");
        }
        try {
            return action.get();
        }
        finally {
            lock.unlock();
        }
    }
}
