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
     * Runs the pipeline for the job on an async executor. The pipeline handles its own terminal transitions
     * (COMPLETED / DRAFT_WITH_WARNINGS / FAILED / CANCELLED incl. clone cleanup); this method is only the
     * last-resort safety net for unhandled errors, so no job can end up running forever — the tray must
     * always be able to show a terminal state, including failures (plan Section 5.4).
     *
     * @param job the claimed job
     */
    @Async("hyperionVariantTaskExecutor")
    public void runJobAsync(VariantJob job) {
        try {
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
                jobService.fail(job.getJobId(), "Unhandled error: " + ex.getMessage());
            }
        }
        finally {
            // Never leak the impersonated authentication to the next task on this pooled thread.
            SecurityContextHolder.clearContext();
        }
    }
}
