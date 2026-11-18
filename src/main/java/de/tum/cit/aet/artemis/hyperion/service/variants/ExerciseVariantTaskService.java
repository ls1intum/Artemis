package de.tum.cit.aet.artemis.hyperion.service.variants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Async dispatch for variant jobs — mirrors {@code HyperionCodeGenerationTaskService}. The @Async boundary is
 * what makes the wizard closable: the job never depends on the client connection; the Hazelcast job record is
 * the source of truth.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ExerciseVariantTaskService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVariantTaskService.class);

    private final ExerciseVariantGenerationPipelineService pipeline;

    private final ExerciseVariantJobService jobService;

    private final VariantQueuedJobHeartbeatService queuedJobHeartbeats;

    public ExerciseVariantTaskService(ExerciseVariantGenerationPipelineService pipeline, ExerciseVariantJobService jobService,
            VariantQueuedJobHeartbeatService queuedJobHeartbeats) {
        this.pipeline = pipeline;
        this.jobService = jobService;
        this.queuedJobHeartbeats = queuedJobHeartbeats;
    }

    /**
     * Runs the pipeline for the job on an async executor. The pipeline handles its own terminal transitions
     * (COMPLETED / DRAFT_WITH_WARNINGS / FAILED / CANCELLED incl. clone cleanup); this method is only the
     * last-resort safety net for unhandled errors, so no job can end up running forever — the tray must
     * always be able to show a terminal state, including failures.
     *
     * A job that already reached a terminal state while it waited for a pool thread is not run at all: the
     * record, not this task, is the source of truth.
     *
     * @param job the claimed job
     */
    @Async("hyperionVariantTaskExecutor")
    public void runJobAsync(VariantJob job) {
        // A worker has it now, so the queue no longer has to vouch for its liveness — the pipeline's own heartbeats do.
        queuedJobHeartbeats.noteLeftQueue(job.getJobId());
        try {
            // The record can have become terminal while this task sat in the executor's queue — cancelled by the
            // instructor, or reconciled as stale. Running it anyway would provision a clone and publish events for a
            // job the tray already shows as finished, and no later transition could correct that.
            if (jobService.getJob(job.getJobId(), job.getInitiatorLogin()).map(current -> current.getPhase().isTerminal()).orElse(true)) {
                log.info("Not running variant generation job {} for exercise {}: it is already terminal or gone", job.getJobId(), job.getSourceExerciseId());
                return;
            }
            // The heartbeat still carries the enqueue time; work starts now, so the first long phase must not be
            // judged against how long the job waited for a pool thread.
            jobService.heartbeat(job.getJobId());
            // The async executor thread has no SecurityContext. Impersonate the initiating instructor: the
            // provisioning path resolves the current user (e.g. exercise channel creation on quiz import) and
            // audit fields should attribute the created entities to the instructor, not to a system user.
            SecurityContextHolder.getContext().setAuthentication(SecurityUtils.makeAuthorizationObject(job.getInitiatorLogin()));
            pipeline.run(job);
        }
        catch (Throwable ex) {
            log.error("Variant generation job {} failed for exercise {}", job.getJobId(), job.getSourceExerciseId(), ex);
            boolean alreadyTerminal = jobService.getJob(job.getJobId(), job.getInitiatorLogin()).map(current -> current.getPhase().isTerminal()).orElse(true);
            if (!alreadyTerminal) {
                // This path deletes nothing — it also catches Errors the pipeline's own terminal catch cannot
                // handle — so the provisioned exercise may well still exist. Keep its id so the tray can still
                // link to it instead of orphaning it silently.
                jobService.failKeepingVariantExerciseId(job.getJobId(), "Unhandled error: " + ex.getMessage());
            }
        }
        finally {
            // Never leak the impersonated authentication to the next task on this pooled thread.
            SecurityContextHolder.clearContext();
        }
    }
}
