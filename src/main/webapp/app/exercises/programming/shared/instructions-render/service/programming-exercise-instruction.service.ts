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
        successfulTests: string[];
        failedTests: string[];
        notExecutedTests: string[];
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
    public testStatusForTask = (tests: string[], latestResult?: Result): TaskResult => {
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

    private separateTests(tests: string[], latestResult: Result) {
        return tests.reduce(
            (acc, testName) => {
                const feedback = latestResult?.feedbacks?.find(({ text }) => text === testName);
                const resultIsLegacy = isLegacyResult(latestResult!);

                // If there is no feedback item, we assume that the test was successful (legacy check).
                if (resultIsLegacy) {
                    return {
                        failed: feedback ? [...acc.failed, testName] : acc.failed,
                        successful: feedback ? acc.successful : [...acc.successful, testName],
                        notExecuted: acc.notExecuted,
                    };
                }

                return {
                    failed: feedback?.positive === false ? [...acc.failed, testName] : acc.failed,
                    successful: feedback?.positive === true ? [...acc.successful, testName] : acc.successful,
                    notExecuted: feedback?.positive === undefined ? [...acc.notExecuted, testName] : acc.notExecuted,
                };
            },
            { failed: [], successful: [], notExecuted: [] },
        );
    }
}
