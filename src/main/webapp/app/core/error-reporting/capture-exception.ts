import { VERSION } from 'app/app.constants';

/**
 * How serious a reported error is. The server logs the report as an info entry, a warning or an error, and counts it in the error analysis, which
 * does not count `INFO`. `CRITICAL` is an error that stops the application from working at all, such as a client that cannot start.
 */
export type ErrorSeverity = 'INFO' | 'WARN' | 'ERROR' | 'CRITICAL';

/**
 * Additional information that is reported together with an error.
 */
export interface ErrorContext {
    /** How serious the error is, `ERROR` if not set. */
    severity?: ErrorSeverity;
    /** Structured data that helps to understand the error, for example the identifiers of the affected entities. */
    extra?: Record<string, unknown>;
    /** Short labels with few distinct values to group related errors, for example the affected feature. */
    tags?: Record<string, string>;
}

/**
 * What the server accepts, see `ClientErrorReportDTO`. The server shortens and cleans every value again, these limits only keep the request below what it accepts.
 */
export interface ClientErrorReport {
    severity: ErrorSeverity;
    message: string;
    stack?: string;
    url: string;
    clientVersion: string;
    extra?: Record<string, string>;
    tags?: Record<string, string>;
}

// The limits of ClientErrorReportDTO. A request that exceeds one is rejected as a whole.
const MAX_MESSAGE_LENGTH = 1000;
const MAX_STACK_LENGTH = 8000;
const MAX_URL_LENGTH = 500;
const MAX_ENTRIES = 10;
const MAX_KEY_LENGTH = 50;
const MAX_VALUE_LENGTH = 500;

/** The endpoint is public: errors on the login page or while the application starts are reported as well. */
const REPORT_URL = 'api/core/public/client-errors';

/** The same error is reported at most once in this time. */
const DUPLICATE_WINDOW_MS = 60_000;
/** At most this many reports leave the page in {@link REPORT_WINDOW_MS}, so a render loop cannot flood the server. */
const MAX_REPORTS_PER_WINDOW = 10;
const REPORT_WINDOW_MS = 60_000;
/** How many different errors are remembered to detect duplicates. */
const MAX_REMEMBERED_ERRORS = 50;
/** How many causes of an error are appended to its stack. */
const MAX_CAUSES = 3;

/**
 * Sends the errors of the client to the server, which writes them to the log.
 * <p>
 * Reporting must never make things worse: it never throws, it never retries, it sends the same error only once per minute and
 * at most {@link MAX_REPORTS_PER_WINDOW} reports per minute. It uses `fetch` instead of the `HttpClient`, so that an error in
 * an interceptor or in the error handling of requests cannot report itself in a loop.
 */
export class ClientErrorReporter {
    private readonly lastReportOfError = new Map<string, number>();
    private reportTimes: number[] = [];

    constructor(
        private readonly send: (report: ClientErrorReport) => void = sendToServer,
        private readonly now: () => number = () => Date.now(),
    ) {}

    /**
     * Reports an error, unless it is a duplicate or too many errors were reported already.
     *
     * @param exception the error, or a message that describes the problem
     * @param context additional information that helps to understand the error
     */
    report(exception: unknown, context?: ErrorContext): void {
        try {
            const report = buildReport(exception, context);
            if (this.isAllowed(report)) {
                this.send(report);
            }
        } catch {
            // nothing to do: an error while reporting an error cannot be reported
        }
    }

    private isAllowed(report: ClientErrorReport): boolean {
        const now = this.now();
        this.reportTimes = this.reportTimes.filter((time) => now - time < REPORT_WINDOW_MS);
        if (this.reportTimes.length >= MAX_REPORTS_PER_WINDOW) {
            return false;
        }

        const key = `${report.severity}|${report.message}|${(report.stack ?? '').slice(0, 300)}`;
        const lastReport = this.lastReportOfError.get(key);
        if (lastReport !== undefined && now - lastReport < DUPLICATE_WINDOW_MS) {
            return false;
        }

        this.lastReportOfError.delete(key);
        this.lastReportOfError.set(key, now);
        if (this.lastReportOfError.size > MAX_REMEMBERED_ERRORS) {
            // a map iterates in insertion order, so the first key is the one reported the longest time ago
            this.lastReportOfError.delete(this.lastReportOfError.keys().next().value!);
        }
        this.reportTimes.push(now);
        return true;
    }
}

/**
 * Turns an error, or anything that was thrown, into a report.
 */
function buildReport(exception: unknown, context?: ErrorContext): ClientErrorReport {
    const error = exception instanceof Error ? exception : undefined;
    const message = error ? error.message || error.name : describe(exception);
    return {
        severity: severityOf(exception, context?.severity ?? 'ERROR'),
        message: message.slice(0, MAX_MESSAGE_LENGTH) || 'Unknown error',
        // a message has no stack: the place that reported it would only show the reporter in a minified build
        stack: error ? stackOf(error) : undefined,
        url: window.location.pathname.slice(0, MAX_URL_LENGTH),
        clientVersion: VERSION,
        extra: toEntries(context?.extra),
        tags: toEntries(context?.tags),
    };
}

/** The status of the failed request that is the error itself or its cause, or undefined if the error does not stem from a failed request. */
function statusOfFailedRequest(exception: unknown): number | undefined {
    let candidate = exception;
    for (let depth = 0; depth <= MAX_CAUSES && candidate !== undefined && candidate !== null; depth++) {
        const response = candidate as { name?: unknown; status?: unknown };
        if (response.name === 'HttpErrorResponse' && typeof response.status === 'number') {
            return response.status;
        }
        candidate = candidate instanceof Error ? candidate.cause : undefined;
    }
    return undefined;
}

const SEVERITY_ORDER: readonly ErrorSeverity[] = ['INFO', 'WARN', 'ERROR', 'CRITICAL'];

/**
 * The severity of a report, which is the requested one unless the error is a failed request that is no error of the application:
 * - A request that never got an answer (status 0, for example offline, blocked or aborted) and one that the proxy answered with 499 because the client gave up are
 *   `INFO`: nobody can fix them on the server.
 * - A gateway that did not get an answer in time (status 502, 503 and 504) is `WARN`, because it points at an overloaded or restarting node, not at a defect of the code.
 * Other statuses keep the requested severity, also a status of 400 to 499: it can be a defect of the client, such as a request to a path that no longer exists, and the
 * client that cannot start because of it must stay visible.
 * An error that failed because of such a request, which is its cause, is classified like the request. An error never gets a higher severity than requested.
 */
function severityOf(exception: unknown, requested: ErrorSeverity): ErrorSeverity {
    const status = statusOfFailedRequest(exception);
    let cap: ErrorSeverity = 'CRITICAL';
    if (status === 0 || status === 499) {
        cap = 'INFO';
    } else if (status === 502 || status === 503 || status === 504) {
        cap = 'WARN';
    }
    return SEVERITY_ORDER.indexOf(cap) < SEVERITY_ORDER.indexOf(requested) ? cap : requested;
}

function describe(exception: unknown): string {
    if (typeof exception === 'string') {
        return exception;
    }
    // An HttpErrorResponse is no Error. Converted to json it would put its whole url, with the query, and the body of the response into the
    // message, and neither belongs into a report: the status and the path tell what failed. The name is checked instead of the type, because
    // errors that reach the error handler can be reconstructed.
    const response = exception as { name?: unknown; status?: unknown; statusText?: unknown; url?: unknown } | null | undefined;
    if (response?.name === 'HttpErrorResponse') {
        return `Http failure response for ${pathOf(response.url)}: ${String(response.status)} ${typeof response.statusText === 'string' ? response.statusText : ''}`.trim();
    }
    return stringify(exception);
}

/** The path of a url without its query and its fragment, which can carry tokens and identifiers. */
function pathOf(url: unknown): string {
    if (typeof url !== 'string' || !url) {
        return '(unknown url)';
    }
    try {
        return new URL(url, window.location.origin).pathname;
    } catch {
        return url.split(/[?#]/)[0];
    }
}

/**
 * The stack of an error followed by the stacks of its causes, as far as they fit. A cause that is no error is described like a reported value, so
 * that a failed request, which is often the cause of a failure, is named by its status and its path and not by its whole url and the response.
 */
function stackOf(error: Error): string | undefined {
    const parts = [error.stack];
    let cause = error.cause;
    for (let depth = 0; cause !== undefined && cause !== null && depth < MAX_CAUSES; depth++) {
        parts.push(`Caused by: ${cause instanceof Error ? (cause.stack ?? `${cause.name}: ${cause.message}`) : describe(cause)}`);
        cause = cause instanceof Error ? cause.cause : undefined;
    }
    const stack = parts.filter((part) => !!part).join('\n');
    return stack ? stack.slice(0, MAX_STACK_LENGTH) : undefined;
}

/** Turns the values of a context into strings that fit the limits of the server, and drops the entries that do not. */
function toEntries(values?: Record<string, unknown>): Record<string, string> | undefined {
    if (!values) {
        return undefined;
    }
    const entries: Record<string, string> = {};
    for (const [key, value] of Object.entries(values).slice(0, MAX_ENTRIES)) {
        if (value !== undefined) {
            entries[key.slice(0, MAX_KEY_LENGTH)] = (typeof value === 'string' ? value : stringify(value)).slice(0, MAX_VALUE_LENGTH);
        }
    }
    return Object.keys(entries).length > 0 ? entries : undefined;
}

/** Never throws, although a value can be circular or can throw when it is converted. */
function stringify(value: unknown): string {
    try {
        return JSON.stringify(value) ?? String(value);
    } catch {
        return Object.prototype.toString.call(value);
    }
}

function sendToServer(report: ClientErrorReport): void {
    if (typeof window.fetch !== 'function') {
        return;
    }
    // The request is not retried and its outcome is not used: the server may be the reason for the error.
    window
        .fetch(REPORT_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(report),
            credentials: 'same-origin',
            // lets the report leave while the page unloads, for example after a failed navigation
            keepalive: true,
        })
        .catch(() => undefined);
}

const reporter = new ClientErrorReporter();

/**
 * Reports an unexpected error that the user cannot fix and that the application recovers from, or cannot recover from, to the server.
 * This is the single entry point of the client for such diagnostics, because a bare {@code console} call is invisible in production.
 * The server writes the report to its log.
 *
 * @param exception the error, or a message that describes the problem
 * @param context additional information that helps to understand the error
 */
export function captureException(exception: unknown, context?: ErrorContext): void {
    reporter.report(exception, context);
}
