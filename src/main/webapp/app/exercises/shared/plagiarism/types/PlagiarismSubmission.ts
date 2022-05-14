import { PlagiarismSubmissionElement } from './PlagiarismSubmissionElement';
import { PlagiarismComparison } from './PlagiarismComparison';

/**
 * Each `PlagiarismSubmission` refers to a submission that has been compared during plagiarism detection.
 * It contains fundamental information independent of the exercise type or algorithm used.
 */
export class PlagiarismSubmission<E extends PlagiarismSubmissionElement> {
    /**
     * ID of the submission.
     */
    id: number;

    /**
     * Login of the student who created the submission.
     */
    studentLogin: string;

    /**
     * List of elements the related submission consists of.
     */
    elements?: E[];

    /**
     * ID of the related submission.
     */
    submissionId: number;

    /**
     * Size of the related submission.
     *
     * For modeling submissions, this is the number of modeling elements.
     * For text and programming submissions, this is the number of words or tokens.
     */
    size: number;

    /**
     * Result score of the related submission.
     */
    score: number;

    /**
     * Comparison of the submission.
     */
    plagiarismComparison: PlagiarismComparison<E>;
}
