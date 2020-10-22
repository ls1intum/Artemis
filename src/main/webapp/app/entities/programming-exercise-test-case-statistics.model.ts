export class ProgrammingExerciseGradingStatistics {
    numParticipations?: number;
    testCaseStatsMap?: TestCaseStatsMap;
    categoryIssuesMap?: CategoryIssuesMap;
    maxIssuesPerCategory?: number;
}

export type TestCaseStatsMap = { [testCase: string]: TestCaseStats };
export type IssuesMap = { [issues: string]: number };
export type CategoryIssuesMap = { [category: string]: IssuesMap };

export class TestCaseStats {
    numPassed?: number;
    numFailed?: number;
}
