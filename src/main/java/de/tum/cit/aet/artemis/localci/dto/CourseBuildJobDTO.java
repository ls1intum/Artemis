package de.tum.cit.aet.artemis.localci.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * A queued or running build job as shown to the instructors of a course.
 * <p>
 * It carries what the build overview of a course displays and leaves out the build configuration, the repository addresses
 * and the details of the build agent, which are only needed on the administration pages.
 *
 * @param id                the unique identifier of the build job
 * @param name              the name of the build job
 * @param buildAgent        the name and display name of the build agent that processes the job, null while the job is queued
 * @param participationId   the participation the job belongs to
 * @param courseId          the course the job belongs to
 * @param exerciseId        the exercise the job belongs to
 * @param retryCount        how often the job was retried
 * @param priority          the priority of the job in the queue
 * @param status            the status of the job
 * @param repositoryInfo    the name and type of the repository the job builds
 * @param jobTimingInfo     the timing information of the job
 * @param commitHashToBuild the commit that is built
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseBuildJobDTO(String id, String name, @Nullable CourseBuildAgentDTO buildAgent, long participationId, long courseId, long exerciseId, int retryCount,
        int priority, @Nullable BuildStatus status, CourseBuildRepositoryDTO repositoryInfo, JobTimingInfo jobTimingInfo, CourseBuildConfigDTO buildConfig) implements BuildJobDTO {

    /**
     * The build agent of a job without its network address.
     *
     * @param name        the name of the build agent
     * @param displayName the display name of the build agent
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseBuildAgentDTO(String name, String displayName) {
    }

    /**
     * The repository a job builds without its addresses.
     *
     * @param repositoryName    the name of the repository
     * @param repositoryType    the type of the repository
     * @param triggeredByPushTo the repository whose push triggered the job
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseBuildRepositoryDTO(String repositoryName, RepositoryType repositoryType, RepositoryType triggeredByPushTo) {
    }

    /**
     * The part of the build configuration that is shown for a job.
     *
     * @param commitHashToBuild the commit that is built
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseBuildConfigDTO(String commitHashToBuild) {
    }

    /**
     * Converts a queued or running build job into its course view.
     *
     * @param job the build job
     * @return the DTO without build configuration, repository addresses and agent address
     */
    public static CourseBuildJobDTO of(BuildJobQueueItem job) {
        var agent = new CourseBuildAgentDTO(job.buildAgent().name(), job.buildAgent().displayName());
        var repository = new CourseBuildRepositoryDTO(job.repositoryInfo().repositoryName(), job.repositoryInfo().repositoryType(), job.repositoryInfo().triggeredByPushTo());
        return new CourseBuildJobDTO(job.id(), job.name(), agent, job.participationId(), job.courseId(), job.exerciseId(), job.retryCount(), job.priority(), job.status(),
                repository, job.jobTimingInfo(), new CourseBuildConfigDTO(job.buildConfig().commitHashToBuild()));
    }
}
