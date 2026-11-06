package de.tum.cit.aet.artemis.exam.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exercise.service.ParticipationCleanupCommand;
import de.tum.cit.aet.artemis.exercise.service.ParticipationCleanupService;

/**
 * Coordinates preparation and retained cleanup under the provider's ordinary AP guarantees. Cleanup intent is recorded
 * after the database commit, not in a durable outbox: a process failure in that gap or loss of provider data still needs
 * operator reconciliation. Retained intent never expires and contains only serializable resource identifiers.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamPreparationLockService {

    private static final Logger log = LoggerFactory.getLogger(ExamPreparationLockService.class);

    private final DistributedDataProvider distributedDataProvider;

    private final ParticipationCleanupService participationCleanupService;

    public ExamPreparationLockService(DistributedDataProvider distributedDataProvider, ParticipationCleanupService participationCleanupService) {
        this.distributedDataProvider = distributedDataProvider;
        this.participationCleanupService = participationCleanupService;
    }

    /**
     * Retries unfinished cleanup before admitting any preparation or regeneration work. The same mutex protects both
     * replay and the new work, so another node cannot recreate resources while a failed old deletion is retried.
     *
     * @param examId the exam being prepared or regenerated
     * @param work   synchronous work, including worker completion and post-commit cleanup
     * @param <T>    the result type
     * @return the work's result
     */
    public <T> T withPreparationLock(long examId, Supplier<T> work) {
        var lock = distributedDataProvider.getLock("exam-participation-preparation-" + examId);
        if (!lock.tryLock(Duration.ZERO)) {
            throw new CannotAcquireLockException("Exam participation preparation or regeneration is already running; retry the request.");
        }
        try {
            try {
                replayCleanup(examId);
            }
            catch (RuntimeException exception) {
                throw new CannotAcquireLockException("External exam resource cleanup is unfinished; preparation remains blocked. Retry the request.", exception);
            }
            return work.get();
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Retains committed cleanup intent before the first external operation. Successful operations are discarded and
     * only failed commands remain for the next guarded request. Call while holding this exam's preparation lock.
     *
     * @param examId   the regenerated exam
     * @param commands resource cleanup captured before database deletion
     */
    public void retainAndRunCleanup(long examId, List<ParticipationCleanupCommand> commands) {
        if (commands.isEmpty()) {
            return;
        }
        cleanupMap().put(examId, List.copyOf(commands));
        replayCleanup(examId);
    }

    private DistributedMap<Long, List<ParticipationCleanupCommand>> cleanupMap() {
        return distributedDataProvider.getMap("exam-unfinished-resource-cleanup");
    }

    private void replayCleanup(long examId) {
        var map = cleanupMap();
        var commands = map.get(examId);
        if (commands == null || commands.isEmpty()) {
            return;
        }
        List<ParticipationCleanupCommand> failedCommands = new ArrayList<>();
        List<RuntimeException> failures = new ArrayList<>();
        for (var command : commands) {
            try {
                participationCleanupService.execute(command);
            }
            catch (RuntimeException exception) {
                log.error("External cleanup {} failed for exam {}", command.operation(), examId, exception);
                failedCommands.add(command);
                failures.add(exception);
            }
        }
        // Persist once per exhausted batch. Replaying after a process failure is safe because deletion is idempotent.
        if (failedCommands.isEmpty()) {
            map.remove(examId);
        }
        else {
            map.put(examId, List.copyOf(failedCommands));
            var failure = new IllegalStateException("Student exam regeneration committed, but external cleanup remains unfinished for exam " + examId);
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }
}
