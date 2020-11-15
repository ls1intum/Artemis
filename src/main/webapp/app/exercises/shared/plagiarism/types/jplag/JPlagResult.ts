import { JPlagComparison } from './JPlagComparison';
import { PlagiarismResult } from '../PlagiarismResult';

/**
 * Result of the automatic plagiarism detection for text or programming exercises.
 */
export class JPlagResult extends PlagiarismResult {
    comparisons: JPlagComparison[];

    /**
     * Duration of the plagiarism detection run in milliseconds.
     */
    duration: number;

    /**
     * Similarity distribution
     */
    similarityDistribution: number[];
}
