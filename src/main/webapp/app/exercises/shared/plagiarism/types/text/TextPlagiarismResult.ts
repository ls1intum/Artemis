import { TextComparison } from './TextComparison';
import { PlagiarismResult } from '../PlagiarismResult';

/**
 * Result of the automatic plagiarism detection for text or programming exercises.
 */
export class TextPlagiarismResult extends PlagiarismResult {
    comparisons: TextComparison[];

    /**
     * Duration of the plagiarism detection run in milliseconds.
     */
    duration: number;

    /**
     * Similarity distribution
     */
    similarityDistribution: number[];
}
