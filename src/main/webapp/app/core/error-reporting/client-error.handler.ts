import { ErrorHandler, Service, inject } from '@angular/core';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';

const RESIZE_OBSERVER_NOTICE = /^ResizeObserver loop (completed with undelivered notifications|limit exceeded)\.?$/;
const UNMATCHED_ROUTE_ERROR = /^NG04002\b/;
/** A frame of a stack in the format of V8, `    at function (url:line:column)`, and in the format of Firefox and Safari, `function@url:line:column`. */
const V8_FRAME = /^\s+at\s/;
const GECKO_OR_WEBKIT_FRAME = /@\S+:\d+(:\d+)?\s*$/;
/** The url of a script of an extension: `chrome-extension://`, `moz-extension://`, `safari-web-extension://` and `safari-extension://`. */
const BROWSER_EXTENSION_URL = /(chrome|moz|safari|safari-web|ms-browser)-extension:\/\//;

/**
 * Reports the errors that nothing else handles to the server, in addition to Angular's default handling, which logs them to the console.
 */
@Service()
export class ClientErrorHandler extends ErrorHandler {
    private localStorageService = inject(LocalStorageService);

    /**
     * Reports an unhandled error, unless it is a failed request with a status of 400 to 499 or one of the errors that no change of the application can avoid. The failed
     * requests are expected, for example a rejected input or a missing permission, and they are shown to the user where they happen. Failed requests with a status of 500
     * or higher, and errors without a response, are reported. The others are:
     * - the notice of the browser that a resize observer could not deliver all notifications in one frame, which is harmless and which every layout that reacts to its
     *   own size triggers,
     * - an error that was thrown inside a browser extension, which the extension owns,
     * - an address that matches no route, which is reported as information only, because the user typed it or followed a link of another page.
     * @param error the error that nothing handled
     */
    override handleError(error: unknown): void {
        // errors that reach the ErrorHandler of Angular can be reconstructed HttpErrorResponses, so the name is checked instead of the type
        const err = error as { name?: string; status?: number; error?: unknown; message?: unknown; originalError?: unknown };
        const isExpectedRequestError = err?.name === 'HttpErrorResponse' && err.status !== undefined && err.status >= 400 && err.status < 500;
        if (!isExpectedRequestError) {
            ClientErrorHandler.report(ClientErrorHandler.unwrap(error));
        }
        super.handleError(error);
    }

    private static report(reported: unknown): void {
        if (ClientErrorHandler.isOutsideOurControl(reported)) {
            return;
        }
        if (ClientErrorHandler.isUnmatchedRoute(reported)) {
            captureException(reported, { severity: 'INFO' });
            return;
        }
        captureException(reported);
    }

    /** Whether the error is a notice of the browser or was thrown by a browser extension, which nothing in the application causes. */
    private static isOutsideOurControl(reported: unknown): boolean {
        if (!(reported instanceof Error)) {
            return false;
        }
        return RESIZE_OBSERVER_NOTICE.test(reported.message) || ClientErrorHandler.isThrownByBrowserExtension(reported.stack);
    }

    /**
     * Whether the error was thrown inside a browser extension. Only the top frame counts: an error that an extension caused in our code, for example by dispatching an event
     * that the application cannot handle, has frames of the extension further down and is an error of the application.
     */
    private static isThrownByBrowserExtension(stack: string | undefined): boolean {
        const topFrame = stack?.split('\n').find((line) => V8_FRAME.test(line) || GECKO_OR_WEBKIT_FRAME.test(line));
        return !!topFrame && BROWSER_EXTENSION_URL.test(topFrame);
    }

    /** The router rejects an address that matches no route with the code NG04002. */
    private static isUnmatchedRoute(reported: unknown): boolean {
        return reported instanceof Error && UNMATCHED_ROUTE_ERROR.test(reported.message);
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
