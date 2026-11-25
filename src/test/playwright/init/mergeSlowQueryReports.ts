/** The parts of the server's query report that are combined across nodes. */
export interface SlowQueryReport {
    repeatedQueries: unknown[];
    /** True if the node dropped findings because a list reached its size limit. */
    truncated: boolean;
}

/** Combines the reports of every core node into one; the result is truncated if any node's report is. */
export function mergeSlowQueryReports(reports: SlowQueryReport[]): SlowQueryReport {
    return reports.reduce((all, report) => ({
        ...all,
        repeatedQueries: [...all.repeatedQueries, ...report.repeatedQueries],
        truncated: all.truncated || report.truncated,
    }));
}
