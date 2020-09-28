export class ProgrammingExerciseGradingStatistics {
    numTestCases: number;
    numParticipations: number;
    testCaseStatsList: TestCaseStats[];
}

export class TestCaseStats {
    testName: string;
    numPassed: number;
    numFailed: number;
}
