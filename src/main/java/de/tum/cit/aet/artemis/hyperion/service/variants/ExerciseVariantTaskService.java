package de.tum.cit.aet.artemis.hyperion.service.variants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Async dispatch for variant jobs — mirrors {@code HyperionCodeGenerationTaskService} (plan Section 5.2).
 * The @Async boundary is what makes the wizard closable: the job never depends on the client connection;
 * the Hazelcast job record is the source of truth (Section 5.4, "Closable wizard").
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ExerciseVariantTaskService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVariantTaskService.class);

    private final ExerciseVariantGenerationPipeline pipeline;

    private final ExerciseVariantJobService jobService;

    public ExerciseVariantTaskService(ExerciseVariantGenerationPipeline pipeline, ExerciseVariantJobService jobService) {
        this.pipeline = pipeline;
        this.jobService = jobService;
    }

    /**
     * Runs the pipeline for the job on an async executor.
     *
     * TODO (Sonnet): Implement, mirroring HyperionCodeGenerationTaskService.runJobAsync:
     * 1. try { pipeline.run(job); }
     * 2. catch (Exception ex): log with jobId + sourceExerciseId; transition the job to FAILED via
     * jobService.fail(jobId, "Unhandled error: " + ex.getMessage()) so a FAILED event reaches the client
     * (plan Section 6, hard-failure row) — the pipeline is responsible for clone cleanup before rethrowing.
     * 3. finally: run the cleanup callback (releases ONLY the per-exercise dedup lock; the job record stays for
     * the tray, plan Section 5.2 "Job retention").
     *
     * @param job     the claimed job
     * @param cleanup releases the per-exercise dedup lock after the job reached a terminal state
     */
    @Async
    public void runJobAsync(VariantJob job, Runnable cleanup) {
        // TODO (Sonnet): implement — see method Javadoc.
        throw new UnsupportedOperationException("TODO (Sonnet): implement async dispatch (plan Section 5.2)");
    }
}
