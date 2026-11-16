import { HttpContext } from '@angular/common/http';
import { AsyncValidatorFn } from '@angular/forms';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { Observable, map, switchMap, timer } from 'rxjs';

/**
 * The server decides which email addresses are allowed, because it applies the restriction that the administrators configured with the same rules
 * to registration and to the change of an address. The forms only ask it while the address is typed, so that nobody has to send the form to find out.
 * A browser could not decide this itself: the restriction is written for Java, and Java and JavaScript read some expressions differently.
 */
export const EMAIL_ALLOWED_URL = 'api/core/public/email-allowed';

/** How long the form waits after the last key press before it asks the server, so that typing does not send a request per letter. */
export const EMAIL_ALLOWED_DEBOUNCE_MS = 500;

/** How long the form waits for the answer of the server before it lets the person continue, so that a server that does not answer cannot block the form. */
export const EMAIL_ALLOWED_TIMEOUT_MS = 5000;

/**
 * The context of the request to {@link EMAIL_ALLOWED_URL}. A failure of this background check must not show the alert for failed requests: the form simply goes on,
 * because the decision is made again when it is sent.
 *
 * @returns the context that suppresses the global alert of the error interceptor
 */
export function emailAllowedRequestContext(): HttpContext {
    return new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true);
}

/** The answer of the server to {@link EMAIL_ALLOWED_URL}. */
export interface EmailAllowedResponse {
    allowed: boolean;
}

/**
 * Validator for a reactive form control that asks the server after a pause in typing whether the address is allowed. It runs only after the other validators
 * (required, length) passed, and a newer value cancels the request of an older one.
 *
 * @param isAllowed asks the server whether the address is allowed. It has to answer {@code true} when the server cannot answer, because the decision is made
 *                      again when the form is sent, so a server that is busy or a limit on requests must not block the form.
 * @param debounceMs the pause after the last change before the server is asked
 * @returns the error {@code emailNotAllowed} if the server refuses the address, otherwise no error
 */
export function emailAllowedValidator(isAllowed: (email: string) => Observable<boolean>, debounceMs = EMAIL_ALLOWED_DEBOUNCE_MS): AsyncValidatorFn {
    return (control) =>
        timer(debounceMs).pipe(
            switchMap(() => isAllowed(control.value)),
            map((allowed) => (allowed ? null : { emailNotAllowed: true })),
        );
}
