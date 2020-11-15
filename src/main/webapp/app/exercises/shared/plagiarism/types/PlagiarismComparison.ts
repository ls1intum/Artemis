import { PlagiarismStatus } from './PlagiarismStatus';
import { PlagiarismComparisonElement } from 'app/exercises/shared/plagiarism/types/PlagiarismComparisonElement';

/**
 * Pair of compared student submissions whose similarity is above a certain threshold.
 */
export class PlagiarismComparison {
    /**
     * First element involved in this comparison.
     */
    elementA: PlagiarismComparisonElement;

    /**
     * Second element involved in this comparison.
     */
    elementB: PlagiarismComparisonElement;

    /**
     * Similarity of the compared submissions (between 0 and 1).
     */
    similarity: number;

    /**
     * Status of this plagiarism.
     */
    status: PlagiarismStatus;
}
