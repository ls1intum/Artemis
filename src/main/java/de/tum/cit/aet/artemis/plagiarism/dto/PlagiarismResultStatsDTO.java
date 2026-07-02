package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Stores statistics about particular plagiarism checks result.
 *
 * @param numberOfDetectedSubmissions number of unique submissions included in the result
 * @param averageSimilarity           average similarity of all comparisons
 * @param maximalSimilarity           maximal similarity in all comparisons
 * @param createdBy                   user or entity which stated the check
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismResultStatsDTO(int numberOfDetectedSubmissions, double averageSimilarity, double maximalSimilarity, @Nullable String createdBy) {
}
