package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionForParticipationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultBeforeEvaluationDTO(Long id, ZonedDateTime completionDate, Boolean rated, SubmissionForParticipationDTO submission) {

    public static ResultBeforeEvaluationDTO of(final Result result) {
        return new ResultBeforeEvaluationDTO(result.getId(), result.getCompletionDate(), result.isRated(), SubmissionForParticipationDTO.of(result.getSubmission()));
    }

}
