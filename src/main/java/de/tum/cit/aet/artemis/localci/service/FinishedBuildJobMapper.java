package de.tum.cit.aet.artemis.localci.service;

import org.springframework.data.domain.Slice;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO.ResultDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;
import de.tum.cit.aet.artemis.exercise.service.ParticipationMapper;
import de.tum.cit.aet.artemis.exercise.service.SubmissionMapper;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;

/** Maps persisted data to the records exchanged with clients and build agents. */
public final class FinishedBuildJobMapper {

    private FinishedBuildJobMapper() {
    }

    /**
     * Converts a Result into a ResultDTO
     *
     * @param result to convert
     * @return the converted DTO
     */
    private static ResultDTO result(Result result) {
        SubmissionDTO submissionDTO = result.getSubmission() == null ? null : SubmissionMapper.of(result.getSubmission(), false, null, null);

        return new ResultDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(),
                ParticipationMapper.of(result.getSubmission().getParticipation()), submissionDTO, result.getAssessmentType(), result.getTestCaseCount(),
                result.getPassedTestCaseCount(), result.getCodeIssueCount());
    }

    /**
     * Converts a Slice of BuildJobs into a Slice of FinishedBuildJobDTOs
     *
     * @param buildJobs to convert
     * @return the converted Slice
     */
    public static Slice<FinishedBuildJobDTO> fromBuildJobsSlice(Slice<BuildJob> buildJobs) {
        return buildJobs.map(FinishedBuildJobMapper::of);
    }

    /**
     * Converts a BuildJob into a FinishedBuildJobDTO
     *
     * @param buildJob to convert
     * @return the converted DTO
     */
    public static FinishedBuildJobDTO of(BuildJob buildJob) {
        ResultDTO resultDTO = buildJob.getResult() == null ? null : result(buildJob.getResult());

        return new FinishedBuildJobDTO(buildJob.getBuildJobId(), buildJob.getName(), buildJob.getBuildAgentAddress(), buildJob.getParticipationId(), buildJob.getCourseId(),
                buildJob.getExerciseId(), buildJob.getBuildStatus(), buildJob.getRepositoryType(), buildJob.getRepositoryName(), buildJob.getTriggeredByPushTo(),
                buildJob.getBuildSubmissionDate(), buildJob.getBuildStartDate(), buildJob.getBuildCompletionDate(), buildJob.getCommitHash(), resultDTO,
                buildJob.getBuildAgentDisplayName());
    }
}
