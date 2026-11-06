package de.tum.cit.aet.artemis.exam.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;

/** Coordinates external participation preparation with regeneration cleanup under the provider's ordinary AP guarantees. */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamPreparationLockService {

    private final DistributedDataProvider distributedDataProvider;

    public ExamPreparationLockService(DistributedDataProvider distributedDataProvider) {
        this.distributedDataProvider = distributedDataProvider;
    }

    /**
     * Keeps preparation out of the interval between regeneration's database commit and external cleanup. The repository
     * row lock still protects atomic student-exam replacement; this mutex only coordinates the external resource lifecycle.
     *
     * @param examId the exam being prepared or regenerated
     * @param work   synchronous work, including worker completion and any post-commit cleanup
     * @param <T>    the result type
     * @return the work's result
     */
    public <T> T withPreparationLock(long examId, Supplier<T> work) {
        var lock = distributedDataProvider.getLock("exam-participation-preparation-" + examId);
        if (!lock.tryLock(Duration.ZERO)) {
            throw new CannotAcquireLockException("Exam participation preparation or regeneration is already running; retry the request.");
        }
        try {
            return work.get();
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Runs every cleanup task, even if an earlier one fails, so that one failing external resource does not leave the
     * resources of the remaining participations orphaned. The first failure is rethrown afterwards with the others attached
     * as suppressed exceptions.
     *
     * @param cleanups the cleanup tasks collected during regeneration
     */
    public void runAllCleanups(List<Runnable> cleanups) {
        RuntimeException failure = null;
        for (Runnable cleanup : cleanups) {
            try {
                cleanup.run();
            }
            catch (RuntimeException ex) {
                if (failure == null) {
                    failure = ex;
                }
                else {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
