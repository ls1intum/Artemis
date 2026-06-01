package de.tum.cit.aet.artemis.plagiarism.dto;

import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismComparisonDTO(Long id, PlagiarismSubmissionDTO submissionA, PlagiarismSubmissionDTO submissionB, Set<PlagiarismMatchDTO> matches, double similarity,
        PlagiarismStatus status) {

    /**
     * Maps a plagiarism comparison entity to the DTO used by the split view.
     *
     * @param comparison the plagiarism comparison entity
     * @return the DTO representation
     */
    public static PlagiarismComparisonDTO fromComparison(PlagiarismComparison comparison) {
        if (comparison == null) {
            return null;
        }
        return fromComparison(comparison, comparison);
    }

    /**
     * Maps a plagiarism comparison loaded in two steps to the DTO used by the split view.
     *
     * @param comparisonWithSubmissionA the comparison whose submission A elements are initialized
     * @param comparisonWithSubmissionB the comparison whose submission B elements are initialized
     * @return the DTO representation
     */
    public static PlagiarismComparisonDTO fromComparison(PlagiarismComparison comparisonWithSubmissionA, PlagiarismComparison comparisonWithSubmissionB) {
        if (comparisonWithSubmissionA == null) {
            return null;
        }

        Set<PlagiarismMatchDTO> matches = null;
        if (comparisonWithSubmissionA.getMatches() != null && Hibernate.isInitialized(comparisonWithSubmissionA.getMatches())) {
            matches = comparisonWithSubmissionA.getMatches().stream().map(PlagiarismMatchDTO::fromMatch).collect(Collectors.toSet());
        }

        return new PlagiarismComparisonDTO(comparisonWithSubmissionA.getId(), PlagiarismSubmissionDTO.fromSubmission(comparisonWithSubmissionA.getSubmissionA()),
                PlagiarismSubmissionDTO.fromSubmission(comparisonWithSubmissionB != null ? comparisonWithSubmissionB.getSubmissionB() : null), matches,
                comparisonWithSubmissionA.getSimilarity(), comparisonWithSubmissionA.getStatus());
    }
}
