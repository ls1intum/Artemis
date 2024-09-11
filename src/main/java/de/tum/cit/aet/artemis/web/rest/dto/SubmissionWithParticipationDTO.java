package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Submission;

/**
 * A DTO representing a submission.
 *
 * @param id            the id of the submission
 * @param participation the participation DTO, the submission belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionWithParticipationDTO(long id, ParticipationWithExerciseDTO participation) {

    /**
     * Converts a submission to a submission DTO.
     *
     * @param submission the submission to convert
     * @return the submission DTO
     */
    public static SubmissionWithParticipationDTO of(Submission submission) {
        return new SubmissionWithParticipationDTO(submission.getId(), ParticipationWithExerciseDTO.of(submission.getParticipation()));
    }
}
