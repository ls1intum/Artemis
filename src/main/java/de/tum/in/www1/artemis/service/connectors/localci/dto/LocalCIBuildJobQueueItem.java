package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.enumeration.BuildJobResult;

public record LocalCIBuildJobQueueItem(String id, String name, String buildAgentAddress, long participationId, long courseId, long exerciseId, int retryCount, int priority,
        BuildJobResult status, RepositoryInfo repositoryInfo, JobTimingInfo jobTimingInfo, BuildConfig buildConfig) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructor used to update a finished build job with the build completion date and result
     *
     * @param queueItem           The queued build job
     * @param buildCompletionDate The date when the build job was completed
     * @param status              The status/result of the build job
     */
    public LocalCIBuildJobQueueItem(LocalCIBuildJobQueueItem queueItem, ZonedDateTime buildCompletionDate, BuildJobResult status) {
        this(queueItem.id(), queueItem.name(), queueItem.buildAgentAddress(), queueItem.participationId(), queueItem.courseId(), queueItem.exerciseId(), queueItem.retryCount(),
                queueItem.priority(), status, queueItem.repositoryInfo(),
                new JobTimingInfo(queueItem.jobTimingInfo.submissionDate(), queueItem.jobTimingInfo.buildStartDate(), buildCompletionDate), queueItem.buildConfig());
    }

    /**
     * Constructor used to create a new processing build job from a queued build job
     *
     * @param queueItem              The queued build job
     * @param hazelcastMemberAddress The address of the hazelcast member that is processing the build job
     */
    public LocalCIBuildJobQueueItem(LocalCIBuildJobQueueItem queueItem, String hazelcastMemberAddress) {
        this(queueItem.id(), queueItem.name(), hazelcastMemberAddress, queueItem.participationId(), queueItem.courseId(), queueItem.exerciseId(), queueItem.retryCount(),
                queueItem.priority(), null, queueItem.repositoryInfo(), new JobTimingInfo(queueItem.jobTimingInfo.submissionDate(), ZonedDateTime.now(), null),
                queueItem.buildConfig());
    }
}
