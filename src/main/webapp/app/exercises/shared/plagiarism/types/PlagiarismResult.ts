import { PlagiarismComparison } from './PlagiarismComparison';

/**
 * Base result of any automatic plagiarism detection.
 */
export class PlagiarismResult {
    /**
     * List of detected comparisons whose similarity is above the specified threshold.
     */
    comparisons: PlagiarismComparison[];
}
