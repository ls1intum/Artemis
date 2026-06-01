package de.tum.cit.aet.artemis.plagiarism.dto;

import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlagiarismSubmissionDTO(Long id, long submissionId, String studentLogin, List<PlagiarismSubmissionElementDTO> elements, int size, Double score) {

    /**
     * Maps a plagiarism submission entity to the DTO used by the split view.
     *
     * @param submission the plagiarism submission entity
     * @return the DTO representation
     */
    public static PlagiarismSubmissionDTO fromSubmission(PlagiarismSubmission submission) {
        if (submission == null) {
            return null;
        }

        List<PlagiarismSubmissionElementDTO> elements = null;
        if (submission.getElements() != null && Hibernate.isInitialized(submission.getElements())) {
            elements = submission.getElements().stream().map(PlagiarismSubmissionElementDTO::fromElement).toList();
        }

        return new PlagiarismSubmissionDTO(submission.getId(), submission.getSubmissionId(), submission.getStudentLogin(), elements, submission.getSize(), submission.getScore());
    }
}
