export class ProgrammingExerciseGradingStatistics {
    numParticipations?: number; // number of the participations with a result
    testCaseStatsMap?: TestCaseStatsMap; // statistics for each test case
    categoryIssuesMap?: CategoryIssuesMap; // statistics for each category
}

export type TestCaseStatsMap = { [testCase: string]: TestCaseStats };
export type IssuesMap = { [issues: string]: number };
export type CategoryIssuesMap = { [category: string]: IssuesMap };

export class TestCaseStats {
    constructor() {
        this.numPassed = 0;
        this.numFailed = 0;
    }

    numPassed: number;
    numFailed: number;
}
