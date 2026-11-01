import { ErrorHandler, Service, inject } from '@angular/core';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';

/**
 * Reports the errors that nothing else handles to the server, in addition to Angular's default handling, which logs them to the console.
 */
@Service()
export class ClientErrorHandler extends ErrorHandler {
    private localStorageService = inject(LocalStorageService);

    /**
     * Reports an unhandled error, unless it is a failed request with a status of 400 to 499. Those are expected, for example a rejected input or a missing permission,
     * and they are shown to the user where they happen. Failed requests with a status of 500 or higher, and errors without a response, are reported.
     * @param error the error that nothing handled
     */
    override handleError(error: unknown): void {
        // errors that reach the ErrorHandler of Angular can be reconstructed HttpErrorResponses, so the name is checked instead of the type
        const err = error as { name?: string; status?: number; error?: unknown; message?: unknown; originalError?: unknown };
        const isExpectedRequestError = err?.name === 'HttpErrorResponse' && err.status !== undefined && err.status >= 400 && err.status < 500;
        if (!isExpectedRequestError) {
            captureException(ClientErrorHandler.unwrap(error));
        }
        super.handleError(error);
    }

    /**
     * Finds the error that is worth reporting. An error is reported as it is, because only the error itself has a stack. Anything else is searched for the error it wraps,
     * and the text of the failed request or the wrapper is the fallback.
     */
    private static unwrap(error: unknown): unknown {
        if (error instanceof Error) {
            return error;
        }
        const wrapper = error as { name?: unknown; error?: unknown; originalError?: unknown; message?: unknown } | null | undefined;
        if (wrapper?.name === 'HttpErrorResponse') {
            // describes itself without the query of its url and without the body of the response, which its message and its error contain
            return error;
        }
        return (wrapper?.error instanceof Error ? wrapper.error : undefined) ?? wrapper?.originalError ?? wrapper?.message ?? error;
    }

    /**
     * Reports that the browser does not support WebAuthn, which is required for the sign in with a passkey.
     * The message is only reported once per day per browser of a user.
     */
    reportIfPasskeyIsNotSupported(): void {
        if (window.PublicKeyCredential) {
            return;
        }
        try {
            if (this.hasBeenReportedToday()) {
                return;
            }
            this.localStorageService.store<Date>('webauthnNotSupportedTimestamp', new Date());
        } catch {
            // The browser blocks its storage or it is full. This runs when the application starts, so it must not stop it, and without the
            // marker the report would be sent again on every start, so it is not sent at all.
            return;
        }
        captureException(new Error('Browser does not support WebAuthn - no Passkey authentication possible'), { severity: 'INFO', tags: { feature: 'Passkey Authentication' } });
    }

    private hasBeenReportedToday(): boolean {
        const lastReported = this.localStorageService.retrieveDate('webauthnNotSupportedTimestamp');
        const today = new Date();
        return !!lastReported && lastReported.getFullYear() === today.getFullYear() && lastReported.getMonth() === today.getMonth() && lastReported.getDate() === today.getDate();
    }
}
