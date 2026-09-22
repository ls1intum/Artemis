package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.repository.AuthoringRunRepository;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;

/** Reconstructs durable restore obligations before a fresh cluster admits exercise writers. */
@Lazy
@Service
@Profile(PROFILE_CORE)
public class GenerationRecoveryBootstrapService {

    static final String READINESS_MAP = "hyperion-recovery-readiness";

    private static final String READY = "restores-loaded";

    private final AuthoringRunRepository runs;

    private final DistributedDataProvider data;

    public GenerationRecoveryBootstrapService(AuthoringRunRepository runs, DistributedDataProvider data) {
        this.runs = runs;
        this.data = data;
    }

    /** Loads bounded pages before opening the cluster-wide writer barrier, even when generation is disabled. */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        DistributedMap<String, Boolean> readiness = data.getMap(READINESS_MAP);
        readiness.lock(READY);
        try {
            if (Boolean.TRUE.equals(readiness.get(READY))) {
                return;
            }
            long after = 0;
            while (true) {
                var exercises = runs.findPendingRestoreExercises(after, PageRequest.of(0, 100));
                if (exercises.isEmpty()) {
                    break;
                }
                for (long exerciseId : exercises) {
                    restoreGuard(exerciseId);
                }
                after = exercises.getLast();
            }
            readiness.put(READY, true);
        }
        finally {
            readiness.unlock(READY);
        }
    }

    private void restoreGuard(long exerciseId) {
        DistributedMap<String, JobInfo> jobs = data.getMap(GenerationJobService.JOB_MAP_NAME);
        String key = String.valueOf(exerciseId);
        jobs.lock(key);
        try {
            // Recheck under the same lock as releases: a page read before successful recovery must not recreate its guard.
            if (jobs.get(key) == null && runs.existsByExerciseIdAndRestoreStartedAtIsNotNullAndRevertedAtIsNull(exerciseId)) {
                Instant now = Instant.now();
                jobs.putIfAbsent(key, new JobInfo(GenerationRevertSlots.RECOVERY_PREFIX + UUID.randomUUID(), "recovery", exerciseId, now, null, null, now, false, null));
            }
        }
        finally {
            jobs.unlock(key);
        }
    }

    /** Refuses writers until a core node has reconstructed the durable obligations of this cluster incarnation. */
    static void requireInitialized(DistributedDataProvider data) {
        if (!Boolean.TRUE.equals(data.<String, Boolean>getMap(READINESS_MAP).get(READY))) {
            throw new ServiceUnavailableAlertException("Exercise recovery is initializing; retry shortly.", "hyperionExerciseGeneration", "generationRecoveryInitializing");
        }
    }

    /**
     * Clears durable recovery only after exact-token, audited operator reconciliation succeeds. A database failure restores the slot before unlocking.
     *
     * @param exerciseId protected exercise
     * @param token      exact operator-confirmed token
     * @param release    existing topology-checked slot release
     * @return whether that exact slot was released
     */
    public boolean reconcile(long exerciseId, String token, BooleanSupplier release) {
        requireInitialized(data);
        DistributedMap<String, JobInfo> jobs = data.getMap(GenerationJobService.JOB_MAP_NAME);
        String key = String.valueOf(exerciseId);
        jobs.lock(key);
        try {
            JobInfo original = jobs.get(key);
            if (original == null || !original.jobId().equals(token) || !release.getAsBoolean()) {
                return false;
            }
            try {
                runs.reconcileRestore(exerciseId);
            }
            catch (RuntimeException failure) {
                jobs.putIfAbsent(key, original);
                throw failure;
            }
            return true;
        }
        finally {
            jobs.unlock(key);
        }
    }
}
