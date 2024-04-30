package de.tum.in.www1.artemis.service.dto;

import java.time.ZonedDateTime;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.domain.enumeration.BuildStatus;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.web.rest.dto.ResultDTO;

/**
 * A DTO representing a finished build job.
 */

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FinishedBuildJobDTO(String id, String name, String buildAgentAddress, long participationId, long courseId, long exerciseId, BuildStatus status, String repositoryName,
        RepositoryType triggeredByPushTo, ZonedDateTime buildStartDate, ZonedDateTime buildCompletionDate, String commitHash, ResultDTO submissionResult) {

    /**
     * Converts a Page of BuildJobs into a Page of FinishedBuildJobDTOs
     *
     * @param buildJobs to convert
     * @return the converted Page
     */
    public static Page<FinishedBuildJobDTO> fromBuildJobsPage(Page<BuildJob> buildJobs) {
        return buildJobs.map(FinishedBuildJobDTO::of);
    }

    /**
     * Converts a BuildJob into a FinishedBuildJobDTO
     *
     * @param buildJob to convert
     * @return the converted DTO
     */
    public static FinishedBuildJobDTO of(BuildJob buildJob) {

        ResultDTO resultDTO = ResultDTO.of(buildJob.getResult());
        return new FinishedBuildJobDTO(buildJob.getBuildJobId(), buildJob.getName(), buildJob.getBuildAgentAddress(), buildJob.getParticipationId(), buildJob.getCourseId(),
                buildJob.getExerciseId(), buildJob.getBuildStatus(), buildJob.getRepositoryName(), buildJob.getRepositoryType(), buildJob.getBuildStartDate(),
                buildJob.getBuildCompletionDate(), buildJob.getCommitHash(), resultDTO);
    }
}
