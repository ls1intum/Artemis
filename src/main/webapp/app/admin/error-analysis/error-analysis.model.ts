/** What the analysis looks at: the release that is running, or a time span counted back from now. */
export const ERROR_ANALYSIS_SPANS = ['RELEASE', 'DAY', 'WEEK', 'MONTH'] as const;
export type ErrorAnalysisSpan = (typeof ERROR_ANALYSIS_SPANS)[number];

/** Where the errors happened. `ALL` shows both. */
export const ERROR_LOCATION_FILTERS = ['ALL', 'SERVER', 'CLIENT'] as const;
export type ErrorLocationFilter = (typeof ERROR_LOCATION_FILTERS)[number];
export type ErrorLocation = Exclude<ErrorLocationFilter, 'ALL'>;

/** The least serious errors that are shown, which also shows the more serious ones. */
export const ERROR_SEVERITY_FILTERS = ['WARN', 'ERROR', 'CRITICAL'] as const;
export type ErrorSeverityFilter = (typeof ERROR_SEVERITY_FILTERS)[number];

/** What the groups can be sorted by. The values are the ones the server accepts, and the ids of the columns of the table. */
export const ERROR_SORT_FIELDS = ['COUNT', 'SEVERITY', 'LOCATION', 'MESSAGE', 'FIRST_SEEN', 'LAST_SEEN'] as const;
export type ErrorSortField = (typeof ERROR_SORT_FIELDS)[number];

/** How many groups a page can have. The server accepts at most the largest of them. */
export const ERROR_PAGE_SIZES = [10, 25, 50, 100];

/** What the analysis shows by default: the errors of the server and the clients in the release that is running, the most frequent first. */
export const DEFAULT_ERROR_ANALYSIS_SPAN: ErrorAnalysisSpan = 'RELEASE';
export const DEFAULT_ERROR_LOCATION_FILTER: ErrorLocationFilter = 'ALL';
export const DEFAULT_ERROR_SEVERITY_FILTER: ErrorSeverityFilter = 'ERROR';
export const DEFAULT_ERROR_SORT_FIELD: ErrorSortField = 'COUNT';
export const DEFAULT_ERROR_PAGE_SIZE = 25;

/** Errors that are alike, grouped by the server. */
export interface ErrorGroup {
    location: ErrorLocation;
    severity: ErrorSeverityFilter;
    /** What the errors have in common: the message with numbers, identifiers and personal data replaced by placeholders. */
    message: string;
    /** How often the errors happened in the selected time span, or in the release. */
    count: number;
    /** When the first of them happened, which can be before the selected time span. */
    firstSeen: string;
    lastSeen: string;
}

/** One page of the groups, with the totals over all pages. */
export interface ErrorAnalysisPage {
    groups: ErrorGroup[];
    /** How many groups there are on all pages. */
    totalGroups: number;
    /** How many errors there are in all of them. */
    totalErrors: number;
}

/** What the server is asked for. */
export interface ErrorAnalysisQuery {
    span: ErrorAnalysisSpan;
    location: ErrorLocationFilter;
    minSeverity: ErrorSeverityFilter;
    sortField: ErrorSortField;
    ascending: boolean;
    /** The number of the page, the first page is 0. */
    page: number;
    pageSize: number;
}
