package de.tum.in.www1.artemis.domain.plagiarism;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * Stores configuration for manual and continuous plagiarism control.
 */
public class PlagiarismDetectionConfig extends DomainObject {

    private float similarityThreshold;

    private int minimumScore;

    private int minimumSize;

    public PlagiarismDetectionConfig() {
    }

    public PlagiarismDetectionConfig(float similarityThreshold, int minimumScore, int minimumSize) {
        this.similarityThreshold = similarityThreshold;
        this.minimumScore = minimumScore;
        this.minimumSize = minimumSize;
    }

    public float getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(float similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getMinimumScore() {
        return minimumScore;
    }

    public void setMinimumScore(int minimumScore) {
        this.minimumScore = minimumScore;
    }

    public int getMinimumSize() {
        return minimumSize;
    }

    public void setMinimumSize(int minimumSize) {
        this.minimumSize = minimumSize;
    }
}
