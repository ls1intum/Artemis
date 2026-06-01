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

        Set<PlagiarismMatchDTO> matches = null;
        if (comparison.getMatches() != null && Hibernate.isInitialized(comparison.getMatches())) {
            matches = comparison.getMatches().stream().map(PlagiarismMatchDTO::fromMatch).collect(Collectors.toSet());
        }

        return new PlagiarismComparisonDTO(comparison.getId(), PlagiarismSubmissionDTO.fromSubmission(comparison.getSubmissionA()),
                PlagiarismSubmissionDTO.fromSubmission(comparison.getSubmissionB()), matches, comparison.getSimilarity(), comparison.getStatus());
    }
}
