/** How far back the analysis looks, counted from now. */
export const ERROR_ANALYSIS_SPANS = ['DAY', 'WEEK', 'MONTH'] as const;
export type ErrorAnalysisSpan = (typeof ERROR_ANALYSIS_SPANS)[number];

/** Where the errors happened. `ALL` shows both. */
export const ERROR_LOCATION_FILTERS = ['ALL', 'SERVER', 'CLIENT'] as const;
export type ErrorLocationFilter = (typeof ERROR_LOCATION_FILTERS)[number];
export type ErrorLocation = Exclude<ErrorLocationFilter, 'ALL'>;

/** The least serious errors that are shown, which also shows the more serious ones. */
export const ERROR_SEVERITY_FILTERS = ['WARN', 'ERROR', 'CRITICAL'] as const;
export type ErrorSeverityFilter = (typeof ERROR_SEVERITY_FILTERS)[number];

/** What the analysis shows by default: the errors of the server and of the clients, from the last day. */
export const DEFAULT_ERROR_ANALYSIS_SPAN: ErrorAnalysisSpan = 'DAY';
export const DEFAULT_ERROR_LOCATION_FILTER: ErrorLocationFilter = 'ALL';
export const DEFAULT_ERROR_SEVERITY_FILTER: ErrorSeverityFilter = 'ERROR';

/** Errors that are alike, grouped by the server. */
export interface ErrorGroup {
    location: ErrorLocation;
    severity: ErrorSeverityFilter;
    /** What the errors have in common: the message with numbers, identifiers and personal data replaced by placeholders. */
    message: string;
    /** How often the errors happened in the selected time span. */
    count: number;
    /** When the first of them happened since the server started, which can be before the selected time span. */
    firstSeen: string;
    lastSeen: string;
}
