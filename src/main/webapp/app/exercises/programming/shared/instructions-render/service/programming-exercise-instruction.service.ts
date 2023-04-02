import { Injectable } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { isLegacyResult } from 'app/exercises/programming/shared/utils/programming-exercise.utils';

/**
 * Enumeration defining state of the test case.
 */
export enum TestCaseState {
    NOT_EXECUTED = 'NOT_EXECUTED',
    SUCCESS = 'SUCCESS',
    FAIL = 'FAIL',
    NO_RESULT = 'NO_RESULT',
}

export type TaskResult = {
    testCaseState: TestCaseState;
    detailed: {
        successfulTests: number[];
        failedTests: number[];
        notExecutedTests: number[];
    };
};

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseInstructionService {
    /**
     * @function testStatusForTask
     * @desc Callback function for renderers to set the appropriate test status
     * @param tests
     * @param latestResult
     */
    public testStatusForTask = (tests: number[], latestResult?: Result): TaskResult => {
        if (latestResult && latestResult.successful && (!latestResult.feedbacks || !latestResult.feedbacks.length) && tests) {
            // Case 1: Submission fulfills all test cases and there are no feedbacks (legacy case), no further checking needed.
            return { testCaseState: TestCaseState.SUCCESS, detailed: { successfulTests: tests, failedTests: [], notExecutedTests: [] } };
        }

        if (latestResult && latestResult.feedbacks && latestResult.feedbacks.length) {
            // Case 2: At least one test case is not successful, tests need to checked to find out if they were not fulfilled
            const { failed, notExecuted, successful } = this.separateTests(tests, latestResult);

            let testCaseState;
            if (failed.length > 0) {
                testCaseState = TestCaseState.FAIL;
            } else if (notExecuted.length > 0 || tests.length === 0) {
                testCaseState = TestCaseState.NOT_EXECUTED;
            } else {
                testCaseState = TestCaseState.SUCCESS;
            }
            return { testCaseState, detailed: { successfulTests: successful, failedTests: failed, notExecutedTests: notExecuted } };
        } else {
            // Case 3: There are no results
            return { testCaseState: TestCaseState.NO_RESULT, detailed: { successfulTests: [], failedTests: [], notExecutedTests: tests } };
        }
    };

    private separateTests(tests: number[], latestResult: Result) {
        return tests.reduce(
            (acc, testId) => {
                const feedback = latestResult?.feedbacks?.find((feedback) => feedback.testCase?.id === testId);
                const resultIsLegacy = isLegacyResult(latestResult!);

                // If there is no feedback item, we assume that the test was successful (legacy check).
                if (resultIsLegacy) {
                    return {
                        failed: feedback ? [...acc.failed, testId] : acc.failed,
                        successful: feedback ? acc.successful : [...acc.successful, testId],
                        notExecuted: acc.notExecuted,
                    };
                }

                return {
                    failed: feedback?.positive === false ? [...acc.failed, testId] : acc.failed,
                    successful: feedback?.positive === true ? [...acc.successful, testId] : acc.successful,
                    notExecuted: feedback?.positive === undefined ? [...acc.notExecuted, testId] : acc.notExecuted,
                };
            },
            { failed: [], successful: [], notExecuted: [] },
        );
    }

    public convertTestListToIds(testList: string): number[] {
        // If there are test names (preview case) map the test to its corresponding id. Else use the id directly provided in the text.
        // split the names by "," only when there is not a closing bracket without a previous opening bracket
        return testList
            .split(/,(?![^(]*?\))/)
            .map((text) => text.trim())
            .map((text) => {
                return this.convertTestToId(text);
            });
    }

    public convertTestToId(test: string) {
        const asId = parseInt(test);
        if (!isNaN(asId)) {
            // If there already is an id, return it directly
            return asId;
        }
        // TODO otherwise find its corresponding id by the test case name (markdown preview case)
        return 2;
    }
}
