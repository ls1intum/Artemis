package de.tum.cit.aet.artemis.plagiarism.dto;

import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismComparisonDTO(Long id, @Nullable PlagiarismSubmissionDTO submissionA, @Nullable PlagiarismSubmissionDTO submissionB,
        @Nullable Set<PlagiarismMatchDTO> matches, double similarity, PlagiarismStatus status) {

    /**
     * Maps a plagiarism comparison entity to the DTO used by the split view.
     *
     * @param comparison the plagiarism comparison entity
     * @return the DTO representation
     */
    public static @Nullable PlagiarismComparisonDTO fromComparison(@Nullable PlagiarismComparison comparison) {
        if (comparison == null) {
            return null;
        }
        boolean submissionsLoaded = comparison.getSubmissions() != null && Hibernate.isInitialized(comparison.getSubmissions());
        return fromComparison(comparison, submissionsLoaded ? comparison.getSubmissionA() : null, submissionsLoaded ? comparison.getSubmissionB() : null);
    }

    /**
     * Maps a plagiarism comparison whose two submissions were read separately to the DTO used by the split view.
     *
     * @param comparison  the plagiarism comparison entity
     * @param submissionA the submission on the first side, with its elements initialized
     * @param submissionB the submission on the second side, with its elements initialized
     * @return the DTO representation
     */
    public static @Nullable PlagiarismComparisonDTO fromComparison(@Nullable PlagiarismComparison comparison, @Nullable PlagiarismSubmission submissionA,
            @Nullable PlagiarismSubmission submissionB) {
        if (comparison == null) {
            return null;
        }

        Set<PlagiarismMatchDTO> matches = comparison.getMatches() != null ? comparison.getMatches().stream().map(PlagiarismMatchDTO::fromMatch).collect(Collectors.toSet()) : null;

        return new PlagiarismComparisonDTO(comparison.getId(), PlagiarismSubmissionDTO.fromSubmission(submissionA), PlagiarismSubmissionDTO.fromSubmission(submissionB), matches,
                comparison.getSimilarity(), comparison.getStatus());
    }
}
