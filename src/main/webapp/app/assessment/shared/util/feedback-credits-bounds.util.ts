import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/foundation/service/alert.service';

/** The message of the bound the server enforces on the points of a feedback, see {@link Feedback.haveCreditsInBounds}. */
export const FEEDBACK_CREDITS_OUT_OF_BOUNDS = 'error.feedbackCreditsOutOfBounds';

/** Whether the server refused an assessment because the points of one of its feedbacks are outside the bounds. */
export function isCreditsOutOfBounds(error: HttpErrorResponse): boolean {
    return error.error?.errorKey === 'feedbackCreditsOutOfBounds';
}

/**
 * Replaces the alerts with the reason why the server refused the update of an assessment after a complaint.
 *
 * @param alertService  the service showing the alert
 * @param response      the answer of the server
 * @param failedMessage the message for any other reason than a complaint lock or points outside the bounds
 */
export function alertUpdateAfterComplaintFailure(alertService: AlertService, response: HttpErrorResponse, failedMessage: string): void {
    alertService.closeAll();
    const error = response.error;
    if (error?.errorKey === 'complaintLock') {
        alertService.error(error.message, error.params);
    } else {
        alertService.error(isCreditsOutOfBounds(response) ? FEEDBACK_CREDITS_OUT_OF_BOUNDS : failedMessage);
    }
}
