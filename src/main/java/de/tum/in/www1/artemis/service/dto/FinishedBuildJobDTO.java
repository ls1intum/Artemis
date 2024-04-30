package de.tum.in.www1.artemis.service.dto;

import java.time.ZonedDateTime;

import org.springframework.data.domain.Page;

import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.domain.enumeration.BuildStatus;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.web.rest.dto.ResultDTO;

public record FinishedBuildJobDTO(String id, String name, String buildAgentAddress, long participationId, long courseId, long exerciseId, BuildStatus status, String repositoryName,
        RepositoryType triggeredByPushTo, ZonedDateTime buildStartDate, ZonedDateTime buildCompletionDate, String commitHash, ResultDTO submissionResult) {

    public static Page<FinishedBuildJobDTO> fromBuildJobsPage(Page<BuildJob> buildJobs) {
        return buildJobs.map(FinishedBuildJobDTO::of);
    }

    public static FinishedBuildJobDTO of(BuildJob buildJob) {

        ResultDTO resultDTO = ResultDTO.of(buildJob.getResult());
        return new FinishedBuildJobDTO(buildJob.getBuildJobId(), buildJob.getName(), buildJob.getBuildAgentAddress(), buildJob.getParticipationId(), buildJob.getCourseId(),
                buildJob.getExerciseId(), buildJob.getBuildStatus(), buildJob.getRepositoryName(), buildJob.getRepositoryType(), buildJob.getBuildStartDate(),
                buildJob.getBuildCompletionDate(), buildJob.getCommitHash(), resultDTO);
    }
}
