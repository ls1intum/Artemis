package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismComparisonSummaryDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double similarity,
        PlagiarismStatus status) {

    public static @Nullable PlagiarismComparisonSummaryDTO fromComparisonSummary(@Nullable PlagiarismComparison comparison) {
        if (comparison == null) {
            return null;
        }
        return new PlagiarismComparisonSummaryDTO(comparison.getId(), comparison.getSimilarity(), comparison.getStatus());
    }
}
