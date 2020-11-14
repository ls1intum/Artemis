import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';

export interface PlagiarismResult<P extends PlagiarismComparison> {
    /**
     * List of comparisons.
     */
    comparisons: P[];
}
