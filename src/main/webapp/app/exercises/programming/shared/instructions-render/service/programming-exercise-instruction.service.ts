import { Injectable } from '@angular/core';
import { ProgrammingExerciseTestCase } from 'app/entities/programming/programming-exercise-test-case.model';
import { Result } from 'app/entities/result.model';

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

const testIdRegex = /<testid>(\d+)<\/testid>/;
const testSplitRegex = /,(?![^(]*?\))/;

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseInstructionService {
    /**
     * @function testStatusForTask
     * @desc Callback function for renderers to set the appropriate test status.
     * @param testIds all test case ids that are included into the task.
     * @param latestResult the result to check for if the tests were successful.
     */
    public testStatusForTask = (testIds: number[], latestResult?: Result): TaskResult => {
        if (latestResult?.successful && (!latestResult.feedbacks || !latestResult.feedbacks.length) && testIds) {
            // Case 1: Submission fulfills all test cases and there are no feedbacks (legacy case), no further checking needed.
            return { testCaseState: TestCaseState.SUCCESS, detailed: { successfulTests: testIds, failedTests: [], notExecutedTests: [] } };
        }

        if (latestResult?.feedbacks?.length) {
            // Case 2: At least one test case is not successful, tests need to checked to find out if they were not fulfilled
            const { failed, notExecuted, successful } = this.separateTests(testIds, latestResult);

            let testCaseState;
            if (failed.length > 0) {
                testCaseState = TestCaseState.FAIL;
            } else if (notExecuted.length > 0 || testIds.length === 0) {
                testCaseState = TestCaseState.NOT_EXECUTED;
            } else {
                testCaseState = TestCaseState.SUCCESS;
            }
            return { testCaseState, detailed: { successfulTests: successful, failedTests: failed, notExecutedTests: notExecuted } };
        } else {
            // Case 3: There are no results
            return { testCaseState: TestCaseState.NO_RESULT, detailed: { successfulTests: [], failedTests: [], notExecutedTests: testIds } };
        }
    };

    private separateTests(tests: number[], latestResult: Result) {
        return tests.reduce(
            (acc, testId) => {
                const feedback = latestResult?.feedbacks?.find((feedback) => feedback.testCase?.id === testId);

                return {
                    failed: feedback?.positive === false ? [...acc.failed, testId] : acc.failed,
                    successful: feedback?.positive === true ? [...acc.successful, testId] : acc.successful,
                    notExecuted: feedback?.positive === undefined ? [...acc.notExecuted, testId] : acc.notExecuted,
                };
            },
            { failed: [], successful: [], notExecuted: [] },
        );
    }

    public convertTestListToIds(testList: string, testCases: ProgrammingExerciseTestCase[] | undefined): number[] {
        // If there are test names, e.g., during the markdown preview, map the test to its corresponding id using the given testCases array.
        // Otherwise, use the id directly provided within the <testid> section.
        // split the tests by "," only when there is not a closing bracket without a previous opening bracket
        return testList
            .split(testSplitRegex)
            .map((text) => text.trim())
            .map((text) => {
                // -1 to indicate a not found test
                return this.convertProblemStatementTextToTestId(text, testCases) ?? -1;
            });
    }

    public convertProblemStatementTextToTestId(test: string, testCases?: ProgrammingExerciseTestCase[]): number | undefined {
        // If the text contains <testid> and </testid>, directly use the number inside
        const match = testIdRegex.exec(test);
        if (match) {
            // If there already is an id, return it directly
            return parseInt(match[1]);
        }
        // otherwise find its corresponding id by the test case name
        return testCases?.find((testCase) => testCase.testName === test)?.id;
    }
}
