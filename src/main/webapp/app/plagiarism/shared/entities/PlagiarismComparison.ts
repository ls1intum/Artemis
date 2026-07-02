import { PlagiarismStatus } from './PlagiarismStatus';
import { PlagiarismSubmission } from './PlagiarismSubmission';
import { PlagiarismMatch } from './PlagiarismMatch';

/**
 * Pair of compared student submissions whose similarity is above a certain threshold.
 */
export class PlagiarismComparison {
    /**
     * Unique identifier of the comparison.
     */
    id: number;

    /**
     * First submission involved in this comparison.
     */
    submissionA: PlagiarismSubmission;

    /**
     * Second submission involved in this comparison.
     */
    submissionB: PlagiarismSubmission;

    /**
     * List of matches between both submissions involved in this comparison.
     */
    matches?: PlagiarismMatch[];

    /**
     * Similarity of the compared submissions in percentage (between 0 and 100).
     */
    similarity: number;

    /**
     * Status of this submission comparison.
     */
    status: PlagiarismStatus;
}

export class PlagiarismComparisonSummary {
    id: number;
    similarity: number;
    status: PlagiarismStatus;
}
