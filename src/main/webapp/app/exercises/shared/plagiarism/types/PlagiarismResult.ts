import { PlagiarismComparison } from './PlagiarismComparison';

/**
 * Base result of any automatic plagiarism detection.
 */
export abstract class PlagiarismResult<P extends PlagiarismComparison> {
    /**
     * List of detected comparisons whose similarity is above the specified threshold.
     */
    comparisons: P[];
}
