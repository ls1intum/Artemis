import { PlagiarismStatus } from './PlagiarismStatus';
import { PlagiarismSubmission } from './PlagiarismSubmission';
import { PlagiarismMatch } from './PlagiarismMatch';
import { PlagiarismSubmissionElement } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmissionElement';
import { Notification } from 'app/entities/notification.model';

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
     * Statement made by student A on the case
     */
    statementA?: string;

    /**
     * Statement made by student B on the case
     */
    statementB?: string;

    /**
     * Status on the Statement student A made
     */
    statusA: PlagiarismStatus;

    /**
     * Status on the Statement student B made
     */
    statusB: PlagiarismStatus;

    /**
     * Notification sent to student A, null if not sent
     */
    notificationA?: Notification;

    /**
     * Notification sent to student B, null if not sent
     */
    notificationB?: Notification;
}
