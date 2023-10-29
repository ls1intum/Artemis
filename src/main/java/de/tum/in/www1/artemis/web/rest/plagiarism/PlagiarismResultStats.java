package de.tum.in.www1.artemis.web.rest.plagiarism;

public record PlagiarismResultStats(int numberOfDetectedSubmissions, double averageSimilarity, double maximalSimilarity, String createdBy) {
}
