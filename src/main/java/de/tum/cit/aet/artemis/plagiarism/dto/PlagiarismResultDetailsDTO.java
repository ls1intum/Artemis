package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.Instant;
import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismResultDetailsDTO(@Nullable Long id, @Nullable List<PlagiarismComparisonDTO> comparisons, long duration, @Nullable List<Integer> similarityDistribution,
        @Nullable Instant createdDate) {

    /**
     * Maps a plagiarism result entity to the DTO returned by plagiarism result endpoints.
     *
     * @param plagiarismResult the plagiarism result entity
     * @return the DTO representation
     */
    public static @Nullable PlagiarismResultDetailsDTO fromResult(@Nullable PlagiarismResult plagiarismResult) {
        if (plagiarismResult == null) {
            return null;
        }

        List<PlagiarismComparisonDTO> comparisons = null;
        if (plagiarismResult.getComparisons() != null && Hibernate.isInitialized(plagiarismResult.getComparisons())) {
            comparisons = plagiarismResult.getComparisons().stream().map(PlagiarismComparisonDTO::fromComparison).toList();
        }

        List<Integer> similarityDistribution = plagiarismResult.getSimilarityDistribution();

        return new PlagiarismResultDetailsDTO(plagiarismResult.getId(), comparisons, plagiarismResult.getDuration(), similarityDistribution, plagiarismResult.getCreatedDate());
    }
}
