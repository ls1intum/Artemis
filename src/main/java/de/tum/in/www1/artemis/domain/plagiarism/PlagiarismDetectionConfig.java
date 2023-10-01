package de.tum.in.www1.artemis.domain.plagiarism;

/**
 * Stores configuration for plagiarism detection.
 */
public record PlagiarismDetectionConfig(float similarityThreshold, int minimumScore, int minimumSize) {
}
