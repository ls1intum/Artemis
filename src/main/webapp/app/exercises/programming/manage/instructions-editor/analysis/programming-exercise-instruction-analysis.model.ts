export type ProblemStatementAnalysis = Array<{
    lineNumber: number;
    invalidTestCases?: string[];
    invalidHints?: string[];
}>;

/**
 * Enumeration specifying the problem statement issues
 */
export enum ProblemStatementIssue {
    INVALID_TEST_CASES = 'invalidTestCases',
    INVALID_HINTS = 'invalidHints',
}

// [line number, issues, issue type]
export type AnalysisItem = [number, string[], ProblemStatementIssue];
