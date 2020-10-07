export class ProgrammingExerciseGradingStatistics {
    numTestCases?: number;
    numParticipations?: number;
    testCaseStatsList?: TestCaseStats[];
    categoryHitMap?: { [category: string]: number }[];
}

export class TestCaseStats {
    testName?: string;
    numPassed?: number;
    numFailed?: number;
}
