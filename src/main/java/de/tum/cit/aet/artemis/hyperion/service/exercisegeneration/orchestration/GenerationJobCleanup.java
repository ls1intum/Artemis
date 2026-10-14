package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobReplayStore.StartedReplay;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;

/** Retries cleanup only for unpublished admissions and workers that have finished. */
final class GenerationJobCleanup {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobCleanup.class);

    private final DistributedMap<String, JobInfo> jobs;

    private final DistributedMap<String, Boolean> cancellations;

    private final GenerationJobReplayStore replay;

    private final BiConsumer<Long, String> publishFinished;

    private final Set<Cleanup> pending = ConcurrentHashMap.newKeySet();

    GenerationJobCleanup(DistributedMap<String, JobInfo> jobs, DistributedMap<String, Boolean> cancellations, GenerationJobReplayStore replay,
            BiConsumer<Long, String> publishFinished) {
        this.jobs = jobs;
        this.cancellations = cancellations;
        this.replay = replay;
        this.publishFinished = publishFinished;
    }

    void unpublished(JobInfo job, @Nullable StartedReplay startedReplay) {
        cleanup(new Cleanup(job.exerciseId(), job.jobId(), job, startedReplay));
    }

    void finished(long exerciseId, String jobId) {
        cleanup(new Cleanup(exerciseId, jobId, null, null));
    }

    void retry() {
        pending.forEach(this::cleanup);
    }

    private void cleanup(Cleanup cleanup) {
        String key = String.valueOf(cleanup.exerciseId());
        try {
            boolean publishCompletion;
            jobs.lock(key);
            try {
                JobInfo current = jobs.get(key);
                if (cleanup.unpublishedJob() != null && current != null && !current.equals(cleanup.unpublishedJob())) {
                    pending.remove(cleanup);
                    return;
                }
                if (cleanup.unpublishedJob() == null) {
                    replay.retainAfterJobCleared(cleanup.exerciseId(), cleanup.jobId());
                }
                else if (cleanup.startedReplay() != null) {
                    replay.restoreUnpublishedStart(cleanup.exerciseId(), cleanup.startedReplay());
                }
                cancellations.remove(cleanup.jobId());
                boolean matching = current != null && (cleanup.unpublishedJob() == null ? current.jobId().equals(cleanup.jobId()) : current.equals(cleanup.unpublishedJob()));
                if (matching && !jobs.remove(key, current)) {
                    throw new IllegalStateException("The generation reservation changed during exact-token cleanup");
                }
                publishCompletion = cleanup.unpublishedJob() == null && (current == null || matching);
            }
            finally {
                jobs.unlock(key);
            }
            pending.remove(cleanup);
            if (publishCompletion) {
                publishFinished.accept(cleanup.exerciseId(), cleanup.jobId());
            }
        }
        catch (RuntimeException failure) {
            if (pending.add(cleanup)) {
                log.warn("Could not clean up completed or unpublished generation {}; exact-token cleanup will be retried.", cleanup.jobId(), failure);
            }
        }
    }

    private record Cleanup(long exerciseId, String jobId, @Nullable JobInfo unpublishedJob, @Nullable StartedReplay startedReplay) {
    }
}
