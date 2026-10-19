package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintDTO;
import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * A submission together with the complaint that was filed against it, as the assessment dashboard lists them.
 *
 * @param submission the submission, already anonymized by the caller
 * @param complaint  the complaint, already anonymized by the caller
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionWithComplaintDTO(SubmissionResponseDTO submission, ComplaintDTO complaint) {

    /**
     * Maps a submission and the complaint filed against it.
     *
     * @param submission the submission to map
     * @param complaint  the complaint to map
     * @return the pair as the assessment dashboard reports it
     */
    public static SubmissionWithComplaintDTO of(Submission submission, Complaint complaint) {
        return new SubmissionWithComplaintDTO(SubmissionResponseDTO.of(submission), ComplaintDTO.of(complaint));
    }
}
