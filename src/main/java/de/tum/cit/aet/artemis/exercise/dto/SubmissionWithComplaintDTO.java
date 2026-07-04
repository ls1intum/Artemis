package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintDTO;
import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * DTO combining an anonymized submission with its complaint for the assessment dashboard.
 *
 * @param submission the DTO-safe, already-filtered submission
 * @param complaint  the DTO-safe, already-filtered complaint
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionWithComplaintDTO(SubmissionResponseDTO submission, ComplaintDTO complaint) {

    /**
     * Maps an already-filtered submission and complaint without reloading participant information.
     *
     * @param submission the filtered submission
     * @param complaint  the filtered complaint
     * @return the complaint dashboard wrapper DTO
     */
    public static SubmissionWithComplaintDTO of(Submission submission, Complaint complaint) {
        return new SubmissionWithComplaintDTO(SubmissionResponseDTO.ofForComplaintDashboard(submission), ComplaintDTO.of(complaint));
    }
}
