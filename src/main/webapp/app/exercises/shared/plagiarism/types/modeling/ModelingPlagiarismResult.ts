import { ModelingComparison } from './ModelingComparison';
import { PlagiarismResult } from '../PlagiarismResult';

/**
 * Result of the automatic plagiarism detection for modeling exercises.
 */
export class ModelingPlagiarismResult extends PlagiarismResult<ModelingComparison> {
    /**
     * Currently, the `ModelingPlagiarismResult` does not have any meta information and only contains
     * a list of comparisons inherited from `PlagiarismResult`.
     */
}
