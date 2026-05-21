package de.tum.cit.aet.artemis.buildagent.dto;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Common interface for build job DTOs, providing access to fields shared between
 * active jobs ({@link BuildJobQueueItem}) and finished jobs ({@link FinishedBuildJobDTO}).
 * <p>
 * This interface enables type-safe handling of build jobs in REST endpoints that
 * may return either type depending on the job's state.
 */
public interface BuildJobDTO {

    /**
     * @return the unique identifier of the build job
     */
    String id();

    /**
     * @return the name/description of the build job
     */
    String name();

    /**
     * @return the ID of the participation this build job belongs to
     */
    long participationId();

    /**
     * @return the ID of the course this build job belongs to
     */
    long courseId();

    /**
     * @return the ID of the exercise this build job belongs to
     */
    long exerciseId();

    /**
     * @return the current status of the build job, or null if not yet determined
     */
    @Nullable
    BuildStatus status();
}
