package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;

// NOTE: this data structure is used in shared code between core and build agent nodes. Changing it requires that the shared data structures in Hazelcast (or potentially Redis)
// in the future are migrated or cleared. Changes should be communicated in release notes as potentially breaking changes.
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildJobQueueItem(@NonNull String id, @NonNull String name, @NonNull BuildAgentDTO buildAgent, long participationId, long courseId, long exerciseId, int retryCount,
        int priority, @Nullable BuildStatus status, @NonNull RepositoryInfo repositoryInfo, @NonNull JobTimingInfo jobTimingInfo, @NonNull BuildConfig buildConfig,
        @Nullable ResultDTO submissionResult, @JsonIgnore @Nullable String cloneToken) implements BuildJobDTO, Serializable, Comparable<BuildJobQueueItem> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructor for a build job that carries no clone token.
     * <p>
     * A queued job normally gets one from {@code LocalCITriggerService}, which is what lets its build agent clone over
     * https without the shared build-agent credential. This overload covers the cases where there is nothing to
     * authenticate: a job that has already finished, an item rebuilt for display, and test fixtures. Such a job falls
     * back to the deprecated configured credential pair.
     */
    public BuildJobQueueItem(String id, String name, BuildAgentDTO buildAgent, long participationId, long courseId, long exerciseId, int retryCount, int priority,
            @Nullable BuildStatus status, RepositoryInfo repositoryInfo, JobTimingInfo jobTimingInfo, BuildConfig buildConfig, @Nullable ResultDTO submissionResult) {
        this(id, name, buildAgent, participationId, courseId, exerciseId, retryCount, priority, status, repositoryInfo, jobTimingInfo, buildConfig, submissionResult, null);
    }

    /**
     * Constructor used to update a finished build job with the build completion date and result
     *
     * @param queueItem           The queued build job
     * @param buildCompletionDate The date when the build job was completed
     * @param status              The status/result of the build job
     */
    public BuildJobQueueItem(BuildJobQueueItem queueItem, ZonedDateTime buildCompletionDate, BuildStatus status) {
        this(queueItem.id(), queueItem.name(), queueItem.buildAgent(), queueItem.participationId(), queueItem.courseId(), queueItem.exerciseId(), queueItem.retryCount(),
                queueItem.priority(), status, queueItem.repositoryInfo(),
                new JobTimingInfo(queueItem.jobTimingInfo.submissionDate(), queueItem.jobTimingInfo.buildStartDate(), buildCompletionDate,
                        queueItem.jobTimingInfo.estimatedCompletionDate(), queueItem.jobTimingInfo.estimatedDuration()),
                // The job has finished, so it is about to leave the processing list and its token stops being accepted.
                // Dropping it here keeps it out of every record of a completed build.
                queueItem.buildConfig(), null, null);
    }

    /**
     * Constructor used to create a new processing build job from a queued build job
     *
     * @param queueItem  The queued build job
     * @param buildAgent The build agent that will process the build job
     */
    public BuildJobQueueItem(BuildJobQueueItem queueItem, BuildAgentDTO buildAgent, ZonedDateTime estimatedCompletionDate) {
        this(queueItem.id(), queueItem.name(), buildAgent, queueItem.participationId(), queueItem.courseId(), queueItem.exerciseId(), queueItem.retryCount(), queueItem.priority(),
                null, queueItem.repositoryInfo(),
                new JobTimingInfo(queueItem.jobTimingInfo.submissionDate(), ZonedDateTime.now(), null, estimatedCompletionDate, queueItem.jobTimingInfo.estimatedDuration()),
                // Must be carried over: this is the entry that lands in the processing list, and that is where a core
                // node looks the token up when the agent clones.
                queueItem.buildConfig(), null, queueItem.cloneToken());
    }

    public BuildJobQueueItem(BuildJobQueueItem queueItem, ResultDTO submissionResult) {
        this(queueItem.id(), queueItem.name(), queueItem.buildAgent(), queueItem.participationId(), queueItem.courseId(), queueItem.exerciseId(), queueItem.retryCount(),
                queueItem.priority(), queueItem.status(), queueItem.repositoryInfo(), queueItem.jobTimingInfo(), queueItem.buildConfig(), submissionResult, queueItem.cloneToken());
    }

    public BuildJobQueueItem(BuildJobQueueItem queueItem, BuildAgentDTO buildAgent, int newRetryCount) {
        this(queueItem.id(), queueItem.name(), buildAgent, queueItem.participationId(), queueItem.courseId(), queueItem.exerciseId(), newRetryCount, queueItem.priority(), null,
                queueItem.repositoryInfo(),
                new JobTimingInfo(queueItem.jobTimingInfo.submissionDate(), ZonedDateTime.now(), null, null, queueItem.jobTimingInfo().estimatedDuration()),
                // A retry keeps the same job id, so the same token stays valid once the job is claimed again.
                queueItem.buildConfig(), null, queueItem.cloneToken());
    }

    @Override
    public int compareTo(BuildJobQueueItem item2) {
        int priorityComparison = Integer.compare(this.priority(), item2.priority());
        if (priorityComparison == 0) {
            return this.jobTimingInfo().submissionDate().compareTo(item2.jobTimingInfo().submissionDate());
        }
        return priorityComparison;
    }

    /**
     * Returns a representation without the clone token.
     * <p>
     * Overridden because the generated record {@code toString} prints every component, and build jobs are logged whole
     * at info level while they are processed. That would write a live credential into the build agent log, from where
     * it would spread to log aggregation and support bundles. {@code @JsonIgnore} does not help here: it governs the
     * REST and websocket payloads, not logging.
     *
     * @return the build job with the clone token replaced by a placeholder
     */
    @Override
    public String toString() {
        return "BuildJobQueueItem[id=" + id + ", name=" + name + ", buildAgent=" + buildAgent + ", participationId=" + participationId + ", courseId=" + courseId + ", exerciseId="
                + exerciseId + ", retryCount=" + retryCount + ", priority=" + priority + ", status=" + status + ", repositoryInfo=" + repositoryInfo + ", jobTimingInfo="
                + jobTimingInfo + ", buildConfig=" + buildConfig + ", submissionResult=" + submissionResult + ", cloneToken=" + (cloneToken == null ? "null" : "***") + "]";
    }
}
