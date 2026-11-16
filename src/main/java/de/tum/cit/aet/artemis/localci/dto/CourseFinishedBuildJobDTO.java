package de.tum.cit.aet.artemis.localci.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * A finished build job as shown to the instructors of a course. Instead of the network address of the build agent it carries the display name of the agent, as recorded when the
 * job finished.
 *
 * @param id                    the unique identifier of the build job
 * @param name                  the name of the build job
 * @param buildAgentDisplayName the display name of the build agent that processed the job, null if no name was recorded and the agent is no longer known
 * @param participationId       the participation the job belongs to
 * @param courseId              the course the job belongs to
 * @param exerciseId            the exercise the job belongs to
 * @param status                the status of the job
 * @param repositoryType        the type of the repository
 * @param repositoryName        the name of the repository
 * @param triggeredByPushTo     the repository whose push triggered the job
 * @param buildSubmissionDate   when the job was submitted
 * @param buildStartDate        when the build started
 * @param buildCompletionDate   when the build finished
 * @param commitHash            the commit that was built
 * @param submissionResult      the result of the build
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseFinishedBuildJobDTO(String id, String name, @Nullable String buildAgentDisplayName, long participationId, long courseId, long exerciseId, BuildStatus status,
        RepositoryType repositoryType, String repositoryName, RepositoryType triggeredByPushTo, ZonedDateTime buildSubmissionDate, ZonedDateTime buildStartDate,
        ZonedDateTime buildCompletionDate, String commitHash, FinishedBuildJobDTO.ResultDTO submissionResult) implements BuildJobDTO {

    private static final Pattern MEMBER_ADDRESS = Pattern.compile("\\[(.+)]:\\d+");

    /**
     * Converts a finished build job into its course view.
     *
     * @param job    the finished build job
     * @param agents the build agents that are currently known, used to resolve names for legacy jobs that have no recorded name
     * @return the DTO without the address of the build agent
     */
    public static CourseFinishedBuildJobDTO of(FinishedBuildJobDTO job, List<BuildAgentInformation> agents) {
        String host = hostOf(job.buildAgentAddress());
        String displayName = job.buildAgentDisplayName();
        if (StringUtils.isBlank(displayName) && host != null) {
            displayName = agents.stream().filter(agent -> agent.buildAgent() != null && host.equals(hostOf(agent.buildAgent().memberAddress())))
                    .map(agent -> StringUtils.defaultIfBlank(agent.buildAgent().displayName(), agent.buildAgent().name())).findFirst().orElse(null);
        }
        return new CourseFinishedBuildJobDTO(job.id(), job.name(), displayName, job.participationId(), job.courseId(), job.exerciseId(), job.status(), job.repositoryType(),
                job.repositoryName(), job.triggeredByPushTo(), job.buildSubmissionDate(), job.buildStartDate(), job.buildCompletionDate(), job.commitHash(),
                job.submissionResult());
    }

    /**
     * The port of a member address changes when an agent reconnects, so agents are matched by their host.
     */
    @Nullable
    private static String hostOf(@Nullable String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        Matcher matcher = MEMBER_ADDRESS.matcher(address);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        int portSeparator = address.lastIndexOf(':');
        return portSeparator > 0 && address.indexOf(':') == portSeparator ? address.substring(0, portSeparator) : address;
    }
}
