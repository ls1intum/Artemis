package de.tum.in.www1.artemis.domain.plagiarism;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * Stores configuration for manual and continuous plagiarism control.
 */
public class PlagiarismDetectionConfig extends DomainObject {

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
