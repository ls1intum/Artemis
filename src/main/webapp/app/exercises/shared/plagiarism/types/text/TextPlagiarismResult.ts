import { TextPlagiarismComparison } from './TextPlagiarismComparison';
import { PlagiarismResult } from '../PlagiarismResult';

/**
 * Result of the automatic plagiarism detection for text or programming exercises.
 */
export class TextPlagiarismResult extends PlagiarismResult {
    comparisons: TextPlagiarismComparison[];
}
