import { JPlagComparison } from './JPlagComparison';
import { PlagiarismResult } from '../PlagiarismResult';

/**
 * Result of the automatic plagiarism detection for text or programming exercises.
 */
export class JPlagResult extends PlagiarismResult<JPlagComparison> {
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
