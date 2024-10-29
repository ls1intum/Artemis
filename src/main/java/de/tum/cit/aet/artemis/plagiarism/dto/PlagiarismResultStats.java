package de.tum.cit.aet.artemis.plagiarism.dto;

/**
 * Stores statistics about particular plagiarism checks result.
 *
 * @param numberOfDetectedSubmissions number of unique submissions included in the result
 * @param averageSimilarity           average similarity of all comparisons
 * @param maximalSimilarity           maximal similarity in all comparisons
 * @param createdBy                   user or entity which stated the check
 */
public record PlagiarismResultStats(int numberOfDetectedSubmissions, double averageSimilarity, double maximalSimilarity, String createdBy) {
}
