import { TextSubmissionElement } from './TextSubmissionElement';
import { PlagiarismResult } from '../PlagiarismResult';

/**
 * Result of the automatic plagiarism detection for text or programming exercises.
 */
export class TextPlagiarismResult extends PlagiarismResult<TextSubmissionElement> {}
