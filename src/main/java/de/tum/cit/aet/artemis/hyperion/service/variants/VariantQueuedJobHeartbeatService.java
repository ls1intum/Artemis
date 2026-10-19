package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Keeps the jobs waiting in <em>this</em> node's variant executor alive.
 * <p>
 * A job's heartbeat says "a worker is still on this", and read-side reconciliation fails any non-terminal job that has
 * not beaten within its stale threshold. A queued job has no worker yet: it carries only the heartbeat that
 * {@code startJob} wrote when it was enqueued. The pool is deliberately bounded and its queue holds up to 32 jobs
 * while a single job runs for minutes, so an instructor's fifth variant can legitimately wait past that threshold and
 * be failed for it — while nothing at all has gone wrong.
 * <p>
 * Excluding queued jobs from staleness instead would trade that bug for a worse one: a job queued on a node that then
 * crashes is lost with the node's in-memory queue, and nothing would ever move it out of the tray. So the node that
 * holds the queue vouches for it, the same way {@code NodeRegistryService} vouches for the node itself — while this
 * node lives, its queued jobs keep beating; when it dies, they stop and are recovered exactly as before. Liveness and
 * dead-node recovery both survive, and no threshold has to be guessed.
 * <p>
 * Registration is explicit rather than derived from the executor, because a {@code ThreadPoolTaskExecutor} exposes how
 * many tasks are queued but not which.
 */
@Lazy(false)
@Service
@Conditional(HyperionEnabled.class)
public class VariantQueuedJobHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(VariantQueuedJobHeartbeatService.class);

    /**
     * Well below the job service's stale threshold, so a queued job misses several refreshes before anything judges
     * it — a slow node or a brief blip must not fail a job that is only waiting.
     */
    private static final int REFRESH_INTERVAL_SECONDS = 60;

    /** Ids of the jobs this node has submitted to the variant executor and that have not started running yet. */
    private final Set<String> jobsQueuedOnThisNode = ConcurrentHashMap.newKeySet();

    private final ExerciseVariantJobService jobService;

    public VariantQueuedJobHeartbeatService(ExerciseVariantJobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Vouches for a job from the moment it is handed to the executor. Call this BEFORE submitting, so the job cannot
     * start — and deregister itself — before it was ever registered.
     *
     * @param jobId the job about to be submitted to this node's variant executor
     */
    public void noteQueued(String jobId) {
        jobsQueuedOnThisNode.add(jobId);
    }

    /**
     * Stops vouching for a job, because it now speaks for itself: its worker started, or the executor refused it.
     *
     * @param jobId the job that left this node's queue
     */
    public void noteLeftQueue(String jobId) {
        jobsQueuedOnThisNode.remove(jobId);
    }

    /**
     * Refreshes the heartbeat of every job still waiting in this node's queue. A job that became terminal in the
     * meantime — cancelled, or reconciled before this ran — is left alone by {@code heartbeat} itself.
     */
    @Scheduled(initialDelay = REFRESH_INTERVAL_SECONDS, fixedRate = REFRESH_INTERVAL_SECONDS, timeUnit = TimeUnit.SECONDS)
    public void refreshQueuedJobHeartbeats() {
        for (String jobId : jobsQueuedOnThisNode) {
            try {
                jobService.heartbeat(jobId);
            }
            catch (Exception e) {
                // One unreachable job must not cost the others their refresh, and nothing may escape a scheduled
                // method — the scheduler would stop invoking it and every queued job would then go stale.
                log.warn("Could not refresh the heartbeat of queued variant job {}: {}", jobId, e.getMessage());
            }
        }
    }
}
