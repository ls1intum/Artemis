package de.tum.cit.aet.artemis.plagiarism.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismComparisonSummaryDTO(Long id, double similarity, PlagiarismStatus status) {

    public static PlagiarismComparisonSummaryDTO fromComparisonSummary(PlagiarismComparison comparison) {
        if (comparison == null) {
            return null;
        }
        return new PlagiarismComparisonSummaryDTO(comparison.getId(), comparison.getSimilarity(), comparison.getStatus());
    }
}
