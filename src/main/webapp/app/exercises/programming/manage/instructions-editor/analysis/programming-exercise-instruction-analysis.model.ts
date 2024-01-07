export type ProblemStatementAnalysis = Map<
    number,
    {
        lineNumber: number;
        invalidTestCases?: string[];
        repeatedTestCases?: string[];
    }
>;

export enum ProblemStatementIssue {
    INVALID_TEST_CASES = 'invalidTestCases',
    REPEATED_TEST_CASES = 'repeatedTestCases',
}

// [line number, issues, issue type]
export type AnalysisItem = [number, string[], ProblemStatementIssue];
