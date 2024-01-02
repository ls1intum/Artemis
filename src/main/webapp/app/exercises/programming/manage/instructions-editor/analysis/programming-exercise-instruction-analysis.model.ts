export type ProblemStatementAnalysis = Map<
    number,
    {
        lineNumber: number;
        invalidTestCases?: string[];
        duplicatedTestCases?: string[];
    }
>;

export enum ProblemStatementIssue {
    INVALID_TEST_CASES = 'invalidTestCases',
    DUPLICATED_TEST_CASES = 'duplicatedTestCases',
}

// [line number, issues, issue type]
export type AnalysisItem = [number, string[], ProblemStatementIssue];
