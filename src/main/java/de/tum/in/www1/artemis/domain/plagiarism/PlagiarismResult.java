package de.tum.in.www1.artemis.domain.plagiarism;

import java.util.List;

/**
 * Base result of any automatic plagiarism detection.
 */
public abstract class PlagiarismResult<E extends PlagiarismSubmissionElement> {

    /**
     * List of detected comparisons whose similarity is above the specified threshold.
     */
    private List<PlagiarismComparison<E>> comparisons;

    /**
     * Duration of the plagiarism detection run in milliseconds.
     */
    private int duration;

    /**
     * ID of the exercise for which plagiarism was detected.
     */
    private long exerciseId;

    /**
     * 10-element array representing the similarity distribution of the detected comparisons.
     * <p>
     * Each entry represents the absolute frequency of comparisons whose similarity lies within the
     * respective interval.
     * <p>
     * Intervals: 0: [0% - 10%), 1: [10% - 20%), 2: [20% - 30%), ..., 9: [90% - 100%]
     */
    private int[] similarityDistribution;

    public List<PlagiarismComparison<E>> getComparisons() {
        return comparisons;
    }

    public void setComparisons(List<PlagiarismComparison<E>> comparisons) {
        this.comparisons = comparisons;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public int[] getSimilarityDistribution() {
        return similarityDistribution;
    }

    public void setSimilarityDistribution(int[] similarityDistribution) {
        this.similarityDistribution = similarityDistribution;
    }
}
