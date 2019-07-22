import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Result } from 'app/entities/result';
import { TestCaseState } from 'app/entities/programming-exercise';
import { isLegacyResult } from 'app/entities/programming-exercise/utils/programming-exercise.utils';

@Injectable()
export class ProgrammingExerciseInstructionService {
    constructor(private translateService: TranslateService) {}

    /**
     * @function statusForTests
     * @desc Callback function for renderers to set the appropiate test status
     * @param tests
     */
    public statusForTests = (tests: string[], latestResult: Result | null): [TestCaseState, string] => {
        const translationBasePath = 'artemisApp.editor.testStatusLabels.';
        const totalTests = tests.length;

        if (latestResult && latestResult.successful && (!latestResult.feedbacks || !latestResult.feedbacks.length)) {
            // Case 1: Submission fulfills all test cases and there are no feedbacks (legacy case), no further checking needed.
            const label = this.translateService.instant(translationBasePath + 'testPassing');
            return [TestCaseState.SUCCESS, label];
        } else if (latestResult && latestResult.feedbacks && latestResult.feedbacks.length) {
            // Case 2: At least one test case is not successful, tests need to checked to find out if they were not fulfilled
            const { failed, notExecuted, successful } = tests.reduce(
                (acc, testName) => {
                    const feedback = latestResult ? latestResult.feedbacks.find(({ text }) => text === testName) : [];
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
            const label = this.translateService.instant(translationBasePath + 'totalTestsPassing', { totalTests, passedTests: successful.length });
            return [testCaseState, label];
        } else {
            // Case 3: There are no results
            const label = this.translateService.instant(translationBasePath + 'noResult');
            return [TestCaseState.NO_RESULT, label];
        }
    };
}
