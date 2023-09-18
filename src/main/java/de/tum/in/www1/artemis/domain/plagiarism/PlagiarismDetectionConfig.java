package de.tum.in.www1.artemis.domain.plagiarism;

/**
 * Stores configuration for plagiarism detection.
 * This class is not a record as in the future (when cpc will be implemented) it will be extended to a DomainObject and represent a database entry.
 */
public class PlagiarismDetectionConfig {

    private final float similarityThreshold;

    private final int minimumScore;

    private final int minimumSize;

    public PlagiarismDetectionConfig(float similarityThreshold, int minimumScore, int minimumSize) {
        this.similarityThreshold = similarityThreshold;
        this.minimumScore = minimumScore;
        this.minimumSize = minimumSize;
    }

    public float getSimilarityThreshold() {
        return similarityThreshold;
    }

    public int getMinimumScore() {
        return minimumScore;
    }

    public int getMinimumSize() {
        return minimumSize;
    }
}
