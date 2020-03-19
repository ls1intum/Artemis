import { Injectable } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { isLegacyResult } from 'app/exercises/programming/shared/utils/programming-exercise.utils';

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
     * @desc Callback function for renderers to set the appropiate test status
     * @param tests
     * @param latestResult
     */
    public testStatusForTask = (tests: string[], latestResult: Result | null): TaskResult => {
        const totalTests = tests.length;
        if (latestResult && latestResult.successful && (!latestResult.feedbacks || !latestResult.feedbacks.length)) {
            // Case 1: Submission fulfills all test cases and there are no feedbacks (legacy case), no further checking needed.
            return { testCaseState: TestCaseState.SUCCESS, detailed: { successfulTests: tests, failedTests: [], notExecutedTests: [] } };
        } else if (latestResult && latestResult.feedbacks && latestResult.feedbacks.length) {
            // Case 2: At least one test case is not successful, tests need to checked to find out if they were not fulfilled
            const { failed, notExecuted, successful } = tests.reduce(
                (acc, testName) => {
                    const feedback = latestResult ? latestResult.feedbacks.find(({ text }) => text === testName) : null;
                    // This is a legacy check, results before the 24th May are considered legacy.
                    const resultIsLegacy = isLegacyResult(latestResult!);
                    // If there is no feedback item, we assume that the test was successful (legacy check).
                    if (resultIsLegacy) {
                        return {
                            failed: feedback ? [...acc.failed, testName] : acc.failed,
                            successful: feedback ? acc.successful : [...acc.successful, testName],
                            notExecuted: acc.notExecuted,
                        };
                    } else {
                        return {
                            failed: feedback && feedback.positive === false ? [...acc.failed, testName] : acc.failed,
                            successful: feedback && feedback.positive === true ? [...acc.successful, testName] : acc.successful,
                            notExecuted: !feedback || feedback.positive === undefined ? [...acc.notExecuted, testName] : acc.notExecuted,
                        };
                    }
                },
                { failed: [], successful: [], notExecuted: [] },
            );

            // Exercise is done if none of the tests failed
            const testCaseState = failed.length > 0 ? TestCaseState.FAIL : notExecuted.length > 0 ? TestCaseState.NOT_EXECUTED : TestCaseState.SUCCESS;
            return { testCaseState, detailed: { successfulTests: successful, failedTests: failed, notExecutedTests: notExecuted } };
        } else {
            // Case 3: There are no results
            return { testCaseState: TestCaseState.NO_RESULT, detailed: { successfulTests: [], failedTests: [], notExecutedTests: tests } };
        }
    };
}
