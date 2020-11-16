import { PlagiarismComparison } from '../PlagiarismComparison';
import { TextPlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismSubmission';

/**
 * Pair of two similar text or source code submissions.
 */
export class TextComparison extends PlagiarismComparison {
    submissionA: TextPlagiarismSubmission;
    submissionB: TextPlagiarismSubmission;
}
