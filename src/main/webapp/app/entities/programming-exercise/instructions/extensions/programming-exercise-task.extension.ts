import { ApplicationRef, EmbeddedViewRef, ComponentFactoryResolver, Injector } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { faCheckCircle, faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import * as showdown from 'showdown';
import { ApollonDiagram, ApollonDiagramService } from 'app/entities/apollon-diagram';
import { ModelingEditorComponent } from 'app/modeling-editor';
import { isLegacyResult } from 'app/entities/programming-exercise/utils/programming-exercise.utils';
import { ProgrammingExerciseInstructionTaskStatusComponent, TestCaseState } from 'app/entities/programming-exercise';
import { Result } from 'app/entities/result';
import { escapeStringForUseInRegex } from 'app/utils/global.utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { EditorInstructionsResultDetailComponent } from 'app/code-editor';

export class ProgrammingExerciseTaskExtensionFactory {
    private latestResult: Result | null = null;

    constructor(
        private componentFactoryResolver: ComponentFactoryResolver,
        private appRef: ApplicationRef,
        private injector: Injector,
        private translateService: TranslateService,
    ) {}

    public setLatestResult(result: Result | null) {
        this.latestResult = result;
    }

    private htmlStatusForTests(tests: string[]) {
        const [done, label] = this.statusForTests(tests);
        const textColor = done === TestCaseState.SUCCESS ? 'text-success' : done === TestCaseState.FAIL ? 'text-danger' : 'text-secondary';

        // If the test is not done, we set the 'data-tests' attribute to the a-element, which we later use for the details dialog
        if (done === TestCaseState.SUCCESS || done === TestCaseState.NO_RESULT || !tests.length) {
            return `<span class="${textColor} bold">` + label + '</span>';
        } else {
            return '<a data-tests="' + tests + `" class="test-status"><span class="${textColor} result">` + label + '</span></a>';
        }
    }

    /**
     * @function statusForTests
     * @desc Callback function for renderers to set the appropiate test status
     * @param tests
     */
    private statusForTests = (tests: string[]): [TestCaseState, string] => {
        const translationBasePath = 'artemisApp.editor.testStatusLabels.';
        const totalTests = tests.length;

        if (this.latestResult && this.latestResult.successful && (!this.latestResult.feedbacks || !this.latestResult.feedbacks.length)) {
            // Case 1: Submission fulfills all test cases and there are no feedbacks (legacy case), no further checking needed.
            const label = this.translateService.instant(translationBasePath + 'testPassing');
            return [TestCaseState.SUCCESS, label];
        } else if (this.latestResult && this.latestResult.feedbacks && this.latestResult.feedbacks.length) {
            // Case 2: At least one test case is not successful, tests need to checked to find out if they were not fulfilled
            const { failed, notExecuted, successful } = tests.reduce(
                (acc, testName) => {
                    const feedback = this.latestResult ? this.latestResult.feedbacks.find(({ text }) => text === testName) : [];
                    // This is a legacy check, results before the 24th May are considered legacy.
                    const resultIsLegacy = isLegacyResult(this.latestResult!);
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

    getExtension() {
        const extension: showdown.ShowdownExtension = {
            type: 'lang',
            filter: (text: string, converter: showdown.Converter, options: showdown.ConverterOptions) => {
                const idPlaceholder = '%idPlaceholder%';
                const taskNamePlaceholder = '%taskNamePlaceholder%';
                const testResultPlaceholder = '%testResultPlaceholder%';
                // E.g. [task][Implement BubbleSort](testBubbleSort)
                const taskRegex = /\[task\]\[.*\]\(.*\)/g;
                // E.g. Implement BubbleSort, testBubbleSort
                const innerTaskRegex = /\[task\]\[(.*)\]\((.*)\)/;
                const taskContainer = `<div id="task-${idPlaceholder}"></div>`;
                const tasks = text.match(taskRegex) || [];
                const testsForTask = tasks
                    .map(task => {
                        const testMatch = task.match(innerTaskRegex);
                        return testMatch && testMatch.length === 3 ? [task, testMatch[1], testMatch[2]] : [];
                    })
                    .map(([task, taskName, tests]: [string, string, string]) => [task, taskName, tests.split(',').map(s => s.trim())]);
                const replacedText = testsForTask.reduce((acc: string, [task, taskName, tests]: [string, string, string[]], index: number): string => {
                    return (
                        acc
                            // Replace task with html structure for task and insert task index.
                            .replace(new RegExp(escapeStringForUseInRegex(task), 'g'), taskContainer.replace(idPlaceholder, index.toString()))
                    );
                    /*                        // Replace task name.
                        .replace(new RegExp(taskNamePlaceholder), taskName)
                        // Replace test result.
                        .replace(new RegExp(testResultPlaceholder), this.htmlStatusForTests(tests));*/
                }, text);
                setTimeout(() => {
                    testsForTask.forEach(([, taskName, tests]: [string, string, string[]], index: number) => {
                        /*                        const [done] = this.statusForTests(tests);*/
                        const componentRef = this.componentFactoryResolver.resolveComponentFactory(ProgrammingExerciseInstructionTaskStatusComponent).create(this.injector);
                        componentRef.instance.taskName = taskName;
                        componentRef.instance.latestResult = this.latestResult;
                        componentRef.instance.tests = tests;

                        this.appRef.attachView(componentRef.hostView);
                        const domElem = (componentRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
                        const iconContainer = document.getElementById(`task-${index}`)!;
                        iconContainer.innerHTML = '';
                        iconContainer.append(domElem);
                    });
                }, 0);
                return replacedText;
            },
        };
        return extension;
    }
}
