package de.tum.cit.aet.artemis.exercise.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.SubmissionVersion;

/**
 * A DTO representing a submission version.
 *
 * @param id          the id of the submission version
 * @param createdDate the date the submission version was created
 * @param content     the content of the submission version
 * @param submission  the submission DTO, the submission version belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionVersionDTO(long id, Instant createdDate, String content, SubmissionWithParticipationDTO submission) {

    /**
     * Converts a submission version to a submission version DTO.
     *
     * @param submissionVersion the submission version to convert
     * @return the submission version DTO
     */
    public static SubmissionVersionDTO of(SubmissionVersion submissionVersion) {
        return new SubmissionVersionDTO(submissionVersion.getId(), submissionVersion.getCreatedDate(), submissionVersion.getContent(),
                SubmissionWithParticipationDTO.of(submissionVersion.getSubmission()));
    }
}
