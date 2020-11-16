import { PlagiarismStatus } from './PlagiarismStatus';
import { PlagiarismSubmission } from './PlagiarismSubmission';
import { PlagiarismMatch } from './PlagiarismMatch';

/**
 * Pair of compared student submissions whose similarity is above a certain threshold.
 */
export class PlagiarismComparison {
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
    matches: PlagiarismMatch[];

    /**
     * Similarity of the compared submissions (between 0 and 1).
     */
    similarity: number;

    /**
     * Status of this submission comparison.
     */
    status: PlagiarismStatus;
}
