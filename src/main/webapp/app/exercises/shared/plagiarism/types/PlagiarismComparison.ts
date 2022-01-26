import { PlagiarismStatus } from './PlagiarismStatus';
import { PlagiarismSubmission } from './PlagiarismSubmission';
import { PlagiarismMatch } from './PlagiarismMatch';
import { PlagiarismSubmissionElement } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmissionElement';
import dayjs from 'dayjs/esm';

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
     * Status of the statement by student A
     */
    statusA: PlagiarismStatus;

    /**
     * Status of the statement of student B
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

    /**
     * Timestamp when instructor set final status for student A
     */
    statusADate?: dayjs.Dayjs;

    /**
     * Timestamp when instructor set final status for student B
     */
    statusBDate?: dayjs.Dayjs;

    /**
     * Timestamp when student A made statement on the case
     */
    studentStatementADate?: dayjs.Dayjs;

    /**
     * Timestamp when student B made statement on the case
     */
    studentStatementBDate?: dayjs.Dayjs;

    /**
     * Timestamp when instructor statement/message sent to student A
     */
    instructorStatementADate?: dayjs.Dayjs;

    /**
     * Timestamp when instructor statement/message sent to student B
     */
    instructorStatementBDate?: dayjs.Dayjs;
}
