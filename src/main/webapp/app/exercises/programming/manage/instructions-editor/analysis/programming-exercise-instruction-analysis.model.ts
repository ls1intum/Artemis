export type ProblemStatementAnalysis = Map<
    number,
    {
        lineNumber: number;
        invalidTestCases?: string[];
        invalidHints?: string[];
    }
>;

export enum ProblemStatementIssue {
    INVALID_TEST_CASES = 'invalidTestCases',
    INVALID_HINTS = 'invalidHints',
}

// [line number, issues, issue type]
export type AnalysisItem = [number, string[], ProblemStatementIssue];
