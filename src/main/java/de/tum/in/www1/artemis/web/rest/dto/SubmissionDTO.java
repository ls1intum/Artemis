package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Submission;

/**
 * A DTO representing a submission.
 *
 * @param id            the id of the submission
 * @param participation the participation DTO, the submission belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionDTO(long id, ParticipationDTO participation) {

    /**
     * Converts a submission to a submission DTO.
     *
     * @param submission the submission to convert
     * @return the submission DTO
     */
    public static SubmissionDTO of(Submission submission) {
        return new SubmissionDTO(submission.getId(), ParticipationDTO.of(submission.getParticipation()));
    }
}
