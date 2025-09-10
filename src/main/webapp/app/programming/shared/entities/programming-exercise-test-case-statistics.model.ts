export class ProgrammingExerciseGradingStatistics {
    numParticipations?: number; // number of the participations with a result
    testCaseStatsMap?: TestCaseStatsMap; // statistics for each test case
    categoryIssuesMap?: CategoryIssuesMap; // statistics for each category
}

export type TestCaseStatsMap = { [testCase: string]: TestCaseStats };
export type IssuesMap = { [issues: string]: number };
export type CategoryIssuesMap = { [category: string]: IssuesMap };

export class TestCaseStats {
    numPassed = 0;
    numFailed = 0;
}
