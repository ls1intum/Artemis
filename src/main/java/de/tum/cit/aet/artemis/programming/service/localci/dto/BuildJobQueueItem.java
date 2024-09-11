package de.tum.cit.aet.artemis.programming.service.localci.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.BuildStatus;
import de.tum.cit.aet.artemis.web.rest.dto.ResultDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildJobQueueItem(String id, String name, String buildAgentAddress, long participationId, long courseId, long exerciseId, int retryCount, int priority,
        BuildStatus status, RepositoryInfo repositoryInfo, JobTimingInfo jobTimingInfo, BuildConfig buildConfig, ResultDTO submissionResult) implements Serializable {

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
        this(queueItem.id(), queueItem.name(), queueItem.buildAgentAddress(), queueItem.participationId(), queueItem.courseId(), queueItem.exerciseId(), queueItem.retryCount(),
                queueItem.priority(), status, queueItem.repositoryInfo(),
                new JobTimingInfo(queueItem.jobTimingInfo.submissionDate(), queueItem.jobTimingInfo.buildStartDate(), buildCompletionDate), queueItem.buildConfig(), null);
    }

    /**
     * Constructor used to create a new processing build job from a queued build job
     *
     * @param queueItem              The queued build job
     * @param hazelcastMemberAddress The address of the hazelcast member that is processing the build job
     */
    public BuildJobQueueItem(BuildJobQueueItem queueItem, String hazelcastMemberAddress) {
        this(queueItem.id(), queueItem.name(), hazelcastMemberAddress, queueItem.participationId(), queueItem.courseId(), queueItem.exerciseId(), queueItem.retryCount(),
                queueItem.priority(), null, queueItem.repositoryInfo(), new JobTimingInfo(queueItem.jobTimingInfo.submissionDate(), ZonedDateTime.now(), null),
                queueItem.buildConfig(), null);
    }

    public BuildJobQueueItem(BuildJobQueueItem queueItem, ResultDTO submissionResult) {
        this(queueItem.id(), queueItem.name(), queueItem.buildAgentAddress(), queueItem.participationId(), queueItem.courseId(), queueItem.exerciseId(), queueItem.retryCount(),
                queueItem.priority(), queueItem.status(), queueItem.repositoryInfo(), queueItem.jobTimingInfo(), queueItem.buildConfig(), submissionResult);
    }
}
