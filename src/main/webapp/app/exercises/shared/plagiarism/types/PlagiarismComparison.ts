import { PlagiarismStatus } from './PlagiarismStatus';
import { PlagiarismSubmission } from './PlagiarismSubmission';
import { PlagiarismMatch } from './PlagiarismMatch';
import { PlagiarismSubmissionElement } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmissionElement';

/**
 * Pair of compared student submissions whose similarity is above a certain threshold.
 */
export class PlagiarismComparison<E extends PlagiarismSubmissionElement> {
    /**
     * Unique identifier of the comparison.
     */
    id: number;

    /**
     * First submission involved in this comparison.
     */
    submissionA: PlagiarismSubmission<E>;

    /**
     * Second submission involved in this comparison.
     */
    submissionB: PlagiarismSubmission<E>;

    /**
     * List of matches between both submissions involved in this comparison.
     */
    matches: PlagiarismMatch[];

    /**
     * Similarity of the compared submissions (between 0 and 1).
     */
    similarity: number;

    /**
     * Status of this submission comparison.
     */
    status: PlagiarismStatus;

    /**
     * Status fn the statement by student A
     */
    statusA: PlagiarismStatus;

    /**
     * Status on the statement of student B
     */
    statusB: PlagiarismStatus;

    /**
     * Statement for the case by student A
     */
    studentStatementA?: string;

    /**
     * Statement for the case by student B
     */
    studentStatementB?: string;

    /**
     * Statement by instructor for student A
     */
    instructorStatementA?: string;

    /**
     * Statement by instructor for student B
     */
    instructorStatementB?: string;
}
