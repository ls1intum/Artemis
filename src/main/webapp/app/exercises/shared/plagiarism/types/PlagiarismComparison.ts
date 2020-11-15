import { PlagiarismStatus } from './PlagiarismStatus';

/**
 * Pair of student submissions whose similarity is above a certain threshold.
 */
export abstract class PlagiarismComparison {
    /**
     * Login of the first student involved in this plagiarism.
     */
    studentA: string;

    /**
     * Login of the second student involved in this plagiarism.
     */
    studentB: string;

    /**
     * Similarity of the detected submissions (between 0 and 1).
     */
    similarity: number;

    /**
     * Status of this plagiarism.
     */
    status: PlagiarismStatus;
}
