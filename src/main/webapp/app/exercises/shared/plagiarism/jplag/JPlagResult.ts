interface JPlagResult {
    /**
     * Number of detected comparisons.
     */
    numberOfComparisons: number;

    /**
     * Total number of compared submissions.
     */
    totalNumberOfComparisons: number;

    /**
     * Duration of the plagiarism detection run in milliseconds.
     */
    duration: number;

    /**
     * List of comparisons.
     */
    comparisons: JPlagComparison[];

    /**
     * Similarity distribution
     */
    similarityDistribution: number[];
}
