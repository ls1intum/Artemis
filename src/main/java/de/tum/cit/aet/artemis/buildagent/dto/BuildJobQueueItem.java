package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
        @Nullable ResultDTO submissionResult) implements Serializable, Comparable<BuildJobQueueItem> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructor used to update a finished build job with the build completion date and result
     *
     * @param queueItem           The queued build job
     * @param buildCompletionDate The date when the build job was completed
     * @param status              The status/result of the build job
     */
    public BuildJobQueueItem(BuildJobQueueItem queueItem, ZonedDateTime buildCompletionDate, BuildStatus status) {
        this(queueItem.id(), queueItem.name(), queueItem.buildAgent(), queueItem.participationId(), queueItem.courseId(), queueItem.exerciseId(), queueItem.retryCount(),
                queueItem.priority(), status, queueItem.repositoryInfo(), new JobTimingInfo(queueItem.jobTimingInfo.submissionDate(), queueItem.jobTimingInfo.buildStartDate(),
                        buildCompletionDate, queueItem.jobTimingInfo.estimatedCompletionDate(), queueItem.jobTimingInfo.estimatedDuration()),
                queueItem.buildConfig(), null);
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
                queueItem.buildConfig(), null);
    }

    public BuildJobQueueItem(BuildJobQueueItem queueItem, ResultDTO submissionResult) {
        this(queueItem.id(), queueItem.name(), queueItem.buildAgent(), queueItem.participationId(), queueItem.courseId(), queueItem.exerciseId(), queueItem.retryCount(),
                queueItem.priority(), queueItem.status(), queueItem.repositoryInfo(), queueItem.jobTimingInfo(), queueItem.buildConfig(), submissionResult);
    }

    public BuildJobQueueItem(BuildJobQueueItem queueItem, BuildAgentDTO buildAgent, int newRetryCount) {
        this(queueItem.id(), queueItem.name(), buildAgent, queueItem.participationId(), queueItem.courseId(), queueItem.exerciseId(), newRetryCount, queueItem.priority(), null,
                queueItem.repositoryInfo(),
                new JobTimingInfo(queueItem.jobTimingInfo.submissionDate(), ZonedDateTime.now(), null, null, queueItem.jobTimingInfo().estimatedDuration()),
                queueItem.buildConfig(), null);
    }

    @Override
    public int compareTo(BuildJobQueueItem item2) {
        int priorityComparison = Integer.compare(this.priority(), item2.priority());
        if (priorityComparison == 0) {
            return this.jobTimingInfo().submissionDate().compareTo(item2.jobTimingInfo().submissionDate());
        }
        return priorityComparison;
    }
}
