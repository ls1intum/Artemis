package de.tum.in.www1.artemis.web.rest.plagiarism;

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
