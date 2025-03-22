package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionForParticipationDTO(Long id, Boolean submitted, SubmissionType type, ZonedDateTime submissionDate) {

    public static SubmissionForParticipationDTO of(final Submission submission) {
        return new SubmissionForParticipationDTO(submission.getId(), submission.isSubmitted(), submission.getType(), submission.getSubmissionDate());
    }

}
