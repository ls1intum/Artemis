export type ProblemStatementAnalysis = Map<
    number,
    {
        lineNumber: number;
        invalidTestCases?: string[];
    }
>;

export enum ProblemStatementIssue {
    INVALID_TEST_CASES = 'invalidTestCases',
}

// [line number, issues, issue type]
export type AnalysisItem = [number, string[], ProblemStatementIssue];
