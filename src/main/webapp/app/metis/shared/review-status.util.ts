import { MetisCheckStatus, MetisDashboardRowDTO } from 'app/metis/shared/entities/metis.model';

/** Where a student's questions stand in the review: not generated yet, waiting for an approval, released to the student, or never generated. */
export type ReviewStatus = 'PREPARING' | 'AWAITING' | 'RELEASED' | 'SKIPPED' | 'FAILED';

/**
 * Classifies a dashboard row for the review by the state of its check, with the rules the server counts the checks that
 * wait for a review by. Only a check in review waits for an approval; without a review, generated questions are
 * released at once, and a check the student has started is past its review. The server releases a check in review
 * with the approval of its last main question, while the review page patches only the approved questions into their
 * row, so a check in review whose main questions are all approved counts as released. Follow-ups are generated live
 * and never reviewed, so they do not count.
 * @param row the participation with its questions in thread order
 */
export function reviewStatusOf(row: MetisDashboardRowDTO): ReviewStatus {
    switch (row.check.status) {
        case MetisCheckStatus.FAILED:
            return 'FAILED';
        case MetisCheckStatus.SKIPPED:
            return 'SKIPPED';
        case MetisCheckStatus.PENDING:
        case MetisCheckStatus.GENERATING:
            return 'PREPARING';
        case MetisCheckStatus.IN_REVIEW: {
            const mainQuestions = row.questions.filter((question) => question.parentQuestionId === undefined);
            return mainQuestions.length > 0 && mainQuestions.every((question) => question.approvedAt !== undefined) ? 'RELEASED' : 'AWAITING';
        }
        case MetisCheckStatus.READY:
        case MetisCheckStatus.IN_PROGRESS:
        case MetisCheckStatus.COMPLETED:
            return 'RELEASED';
    }
}
