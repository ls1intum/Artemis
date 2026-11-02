import { HttpErrorResponse } from '@angular/common/http';

/** The message of the bound the server enforces on the points of a feedback, see {@link Feedback.haveCreditsInBounds}. */
export const FEEDBACK_CREDITS_OUT_OF_BOUNDS = 'error.feedbackCreditsOutOfBounds';

/** Whether the server refused an assessment because the points of one of its feedbacks are outside the bounds. */
export function isCreditsOutOfBounds(error: HttpErrorResponse): boolean {
    return error.error?.errorKey === 'feedbackCreditsOutOfBounds';
}
