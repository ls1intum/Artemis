import { JPlagComparison } from 'app/exercises/shared/plagiarism/types/jplag/JPlagComparison';
import { PlagiarismResult } from 'app/exercises/shared/plagiarism/types/PlagiarismResult';

export interface JPlagResult extends PlagiarismResult<JPlagComparison> {
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
     * Similarity distribution
     */
    similarityDistribution: number[];
}
