/**
 * Additional information that is reported together with an error.
 */
export interface ErrorContext {
    /** Structured data that helps to understand the error, for example the identifiers of the affected entities. */
    extra?: Record<string, unknown>;
    /** Short labels with few distinct values to group related errors, for example the affected feature. */
    tags?: Record<string, string>;
}

/**
 * Reports an unexpected error that the user cannot fix and that the application recovers from, or cannot recover from.
 * This is the single entry point of the client for such diagnostics, because a bare {@code console} call is invisible in production.
 *
 * @param exception the error, or a message that describes the problem
 * @param context additional information that helps to understand the error
 */
export function captureException(exception: unknown, context?: ErrorContext): void {
    // eslint-disable-next-line no-console, no-undef -- the reporter is the one place that writes diagnostics to the console
    console.error(exception, context);
}
