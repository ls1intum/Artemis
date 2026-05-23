package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.Instant;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismResultDetailsDTO(Long id, List<PlagiarismComparisonDTO> comparisons, long duration, List<Integer> similarityDistribution, Instant createdDate,
        String createdBy) {

    public static PlagiarismResultDetailsDTO fromResult(PlagiarismResult plagiarismResult) {
        if (plagiarismResult == null) {
            return null;
        }

        List<PlagiarismComparisonDTO> comparisons = null;
        if (plagiarismResult.getComparisons() != null && Hibernate.isInitialized(plagiarismResult.getComparisons())) {
            comparisons = plagiarismResult.getComparisons().stream().map(PlagiarismComparisonDTO::fromComparison).toList();
        }

        List<Integer> similarityDistribution = null;
        try {
            similarityDistribution = plagiarismResult.getSimilarityDistribution();
        }
        catch (NullPointerException ignored) {
            // Older tests and partially initialized results may not carry a distribution yet.
        }

        return new PlagiarismResultDetailsDTO(plagiarismResult.getId(), comparisons, plagiarismResult.getDuration(), similarityDistribution, plagiarismResult.getCreatedDate(),
                plagiarismResult.getCreatedBy());
    }
}
