package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismSubmissionForCaseDTO(Long id, long submissionId, @Nullable String studentLogin, int size, @Nullable Double score,
        @Nullable PlagiarismComparisonSummaryDTO plagiarismComparison) {

    /**
     * Maps a plagiarism submission entity to the compact DTO used in plagiarism case details.
     *
     * @param submission the plagiarism submission entity
     * @return the DTO representation
     */
    public static @Nullable PlagiarismSubmissionForCaseDTO fromSubmissionForCase(@Nullable PlagiarismSubmission submission) {
        if (submission == null) {
            return null;
        }
        return new PlagiarismSubmissionForCaseDTO(submission.getId(), submission.getSubmissionId(), submission.getStudentLogin(), submission.getSize(), submission.getScore(),
                PlagiarismComparisonSummaryDTO.fromComparisonSummary(submission.getPlagiarismComparison()));
    }
}
