package de.tum.cit.aet.artemis.buildagent.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * A DTO representing a finished build job. The recorded agent name is carried internally for the course view; it is omitted from the administration JSON contract.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FinishedBuildJobDTO(String id, String name, String buildAgentAddress, long participationId, long courseId, long exerciseId, BuildStatus status,
        RepositoryType repositoryType, String repositoryName, RepositoryType triggeredByPushTo, ZonedDateTime buildSubmissionDate, ZonedDateTime buildStartDate,
        ZonedDateTime buildCompletionDate, String commitHash, ResultDTO submissionResult, @JsonIgnore @Nullable String buildAgentDisplayName) implements BuildJobDTO {

    /**
     * A DTO representing a result
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ResultDTO(Long id, ZonedDateTime completionDate, Boolean successful, Double score, Boolean rated, ParticipationDTO participation, SubmissionDTO submission,
            AssessmentType assessmentType, Integer testCaseCount, Integer passedTestCaseCount, Integer codeIssueCount) {
    }

}
