import { ModelingComparison } from './ModelingComparison';
import { PlagiarismResult } from '../PlagiarismResult';

/**
 * Result of the automatic plagiarism detection for modeling exercises.
 */
export class ModelingPlagiarismResult extends PlagiarismResult {
    comparisons: ModelingComparison[];
}
