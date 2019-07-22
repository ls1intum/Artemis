import { ApplicationRef, Component, ComponentFactoryResolver, Injector, Input, SimpleChanges, OnChanges } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { EditorInstructionsResultDetailComponent } from 'app/code-editor/instructions/code-editor-instructions-result-detail';
import { Result } from 'app/entities/result';
import { TestCaseState } from 'app/entities/programming-exercise';
import { isLegacyResult } from 'app/entities/programming-exercise/utils/programming-exercise.utils';

@Component({
    selector: 'jhi-programming-exercise-instructions-task-status',
    template: `
        <div>
            <fa-icon *ngIf="testCaseState === TestCaseState.SUCCESS" [icon]="['far', 'check-circle']" size="lg" class="text-success"></fa-icon>
            <fa-icon *ngIf="testCaseState === TestCaseState.FAIL" [icon]="['far', 'times-circle']" size="lg" class="text-danger"></fa-icon>
            <fa-icon
                *ngIf="testCaseState === TestCaseState.NO_RESULT || testCaseState === TestCaseState.NOT_EXECUTED"
                [icon]="['far', 'question-circle']"
                size="lg"
                class="text-secondary"
            ></fa-icon>
            <span *ngIf="taskName">{{ taskName }}</span>
            <span>
                <span
                    *ngIf="testCaseState === TestCaseState.SUCCESS || testCaseState === TestCaseState.NO_RESULT || !tests.length"
                    [class.text-success]="testCaseState === TestCaseState.SUCCESS"
                    [class.text-secondary]="testCaseState === TestCaseState.NO_RESULT"
                    >{{ testResultLabel }}</span
                >
                <span
                    *ngIf="testCaseState === TestCaseState.FAIL || testCaseState === TestCaseState.NOT_EXECUTED"
                    class="test-status--linked"
                    [class.danger]="testCaseState === TestCaseState.FAIL"
                    [class.text-secondary]="testCaseState === TestCaseState.NOT_EXECUTED"
                    (click)="showDetailsForTests()"
                    >{{ testResultLabel }}</span
                >
            </span>
        </div>
    `,
    styles: ['.test-status--linked {text-decoration: underline; cursor: pointer}'],
})
export class ProgrammingExerciseInstructionTaskStatusComponent {
    TestCaseState = TestCaseState;

    @Input() taskName: string;
    @Input()
    get tests() {
        return this.testsValue;
    }
    @Input() latestResult: Result | null;

    private translationBasePath = 'artemisApp.editor.testStatusLabels.';

    testsValue: string[];
    testCaseState: TestCaseState;
    testResultLabel: string;

    constructor(
        private componentFactoryResolver: ComponentFactoryResolver,
        private appRef: ApplicationRef,
        private injector: Injector,
        private translateService: TranslateService,
        private modalService: NgbModal,
    ) {}

    set tests(tests: string[]) {
        this.testsValue = tests;
        const [testCaseState, label] = this.statusForTests(this.tests, this.latestResult);
        this.testCaseState = testCaseState;
        this.testResultLabel = label;
    }

    // TODO: This should be moved to a service.
    /**
     * @function statusForTests
     * @desc Callback function for renderers to set the appropiate test status
     * @param tests
     */
    private statusForTests = (tests: string[], latestResult: Result | null): [TestCaseState, string] => {
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
            this.testResultLabel = label;
            return [testCaseState, label];
        } else {
            // Case 3: There are no results
            const label = this.translateService.instant(translationBasePath + 'noResult');
            return [TestCaseState.NO_RESULT, label];
        }
    };

    /**
     * @function showDetailsForTests
     * @desc Opens the ResultDetailComponent as popup; displays test results
     * @param result {Result} Result object, mostly latestResult
     * @param tests {string} Identifies the testcase
     */
    public showDetailsForTests() {
        if (!this.latestResult) {
            return;
        }
        const modalRef = this.modalService.open(EditorInstructionsResultDetailComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.result = this.latestResult;
        modalRef.componentInstance.tests = this.tests;
    }
}
