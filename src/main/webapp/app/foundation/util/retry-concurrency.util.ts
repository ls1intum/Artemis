import { HttpErrorResponse } from '@angular/common/http';
import { MonoTypeOperatorFunction, retry, throwError, timer } from 'rxjs';

/** Recognizes the server's retryable lock conflict without retrying other HTTP 409 responses. */
export function isConcurrencyFailure(error: unknown): error is HttpErrorResponse {
    return error instanceof HttpErrorResponse && error.status === 409 && error.error?.message === 'error.concurrencyFailure';
}

/** Retries a rejected operation three times; unsubscribing cancels both requests and backoff timers. */
export function retryOnConcurrencyFailure<T>(): MonoTypeOperatorFunction<T> {
    return retry({
        count: 3,
        delay: (error: unknown, retryCount) => (isConcurrencyFailure(error) ? timer(250 * 2 ** (retryCount - 1)) : throwError(() => error)),
    });
}
