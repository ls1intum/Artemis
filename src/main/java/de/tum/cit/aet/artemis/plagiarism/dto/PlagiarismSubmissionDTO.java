package de.tum.cit.aet.artemis.plagiarism.dto;

import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismSubmissionDTO(Long id, long submissionId, @Nullable String studentLogin, @Nullable List<PlagiarismSubmissionElementDTO> elements, int size,
        @Nullable Double score) {

    /**
     * Maps a plagiarism submission entity to the DTO used by the split view.
     *
     * @param submission the plagiarism submission entity
     * @return the DTO representation
     */
    public static @Nullable PlagiarismSubmissionDTO fromSubmission(@Nullable PlagiarismSubmission submission) {
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
