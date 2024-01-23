package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.enumeration.BuildJobResult;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;

public record LocalCIBuildJobQueueItem(String id, String name, String buildAgentAddress, long participationId, String repositoryName, RepositoryType repositoryType,
        String commitHash, ZonedDateTime submissionDate, int retryCount, ZonedDateTime buildStartDate, ZonedDateTime buildCompletionDate, int priority, long courseId,
        RepositoryType triggeredByPushTo, String dockerImage, BuildJobResult status) implements Serializable {

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
        this(queueItem.id(), queueItem.name(), queueItem.buildAgentAddress(), queueItem.participationId(), queueItem.repositoryName(), queueItem.repositoryType(),
                queueItem.commitHash(), queueItem.submissionDate(), queueItem.retryCount(), queueItem.buildStartDate(), buildCompletionDate, queueItem.priority(),
                queueItem.courseId(), queueItem.triggeredByPushTo(), queueItem.dockerImage(), status);
    }

    /**
     * Constructor used to create a new processing build job from a queued build job
     *
     * @param buildJob               The queued build job
     * @param hazelcastMemberAddress The address of the hazelcast member that is processing the build job
     */
    public LocalCIBuildJobQueueItem(LocalCIBuildJobQueueItem buildJob, String hazelcastMemberAddress) {
        this(buildJob.id(), buildJob.name(), hazelcastMemberAddress, buildJob.participationId(), buildJob.repositoryName(), buildJob.repositoryType(), buildJob.commitHash(),
                buildJob.submissionDate(), buildJob.retryCount(), ZonedDateTime.now(), null, buildJob.priority(), buildJob.courseId(), buildJob.triggeredByPushTo(), null, null);
    }
}
