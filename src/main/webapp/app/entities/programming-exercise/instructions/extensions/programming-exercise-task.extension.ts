import { ApplicationRef, EmbeddedViewRef, ComponentFactoryResolver, Injector } from '@angular/core';
import * as showdown from 'showdown';
import { ApollonDiagram, ApollonDiagramService } from 'app/entities/apollon-diagram';
import { ModelingEditorComponent } from 'app/modeling-editor';
import { isLegacyResult } from 'app/entities/programming-exercise/utils/programming-exercise.utils';
import { TestCaseState } from 'app/entities/programming-exercise';
import { Result } from 'app/entities/result';
import { escapeStringForUseInRegex } from 'app/utils/global.utils';

/*/!**
 * @function statusForTests
 * @desc Callback function for renderers to set the appropiate test status
 * @param tests
 *!/
const statusForTests = (tests: string[], latestResult: Result | null): [TestCaseState, string] => {
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
};*/

export const ProgrammingExerciseTaskExtension = (componentFactoryResolver: ComponentFactoryResolver, appRef: ApplicationRef, injector: Injector) => {
    const extension: showdown.ShowdownExtension = {
        type: 'lang',
        filter: (text: string, converter: showdown.Converter, options: showdown.ConverterOptions) => {
            const idPlaceholder = '%idPlaceholder%';
            // E.g. [task][Implement BubbleSort](testBubbleSort)
            const taskRegex = /\[task\]\[.*\]\(.*\)/g;
            // E.g. testBubbleSort
            const testRegex = /\((.*)\)/;
            const taskContainer = `<div id="task-${idPlaceholder}"></div>`;
            const tasks = text.match(taskRegex) || [];
            const testsForTask = tasks
                .map(task => {
                    const testMatch = task.match(testRegex);
                    return testMatch && testMatch.length === 2 ? [task, testMatch[1]] : [];
                })
                .map(([task, tests]: [string, string]) => [task, tests.split(',').map(s => s.trim())]);
            const replacedText = tasks.reduce(
                (acc: string, task: string, index: number): string =>
                    acc.replace(new RegExp(escapeStringForUseInRegex(task), 'g'), taskContainer.replace(idPlaceholder, index.toString())),
                text,
            );
            return replacedText;
            /*            const idPlaceholder = '%idPlaceholder%';
            const regex = /(?!\[apollon\])(\d+?)(?=\[\/apollon\])/g;
            const regexTemplate = `\\[apollon\\]${idPlaceholder}\\[\\/apollon\\]`;
            const apollonHtmlContainer = `<div id="apollon-${idPlaceholder}"></div>`;
            const apollonDiagrams: string[] = text.match(regex) || [];
            const replacedText = apollonDiagrams.reduce(
                (acc: string, id: string): string => acc.replace(new RegExp(regexTemplate.replace(idPlaceholder, id), 'g'), apollonHtmlContainer.replace(idPlaceholder, id)),
                text,
            );
            apollonDiagrams
                .reduce((acc: string[], x: string) => (acc.includes(x) ? acc : [...acc, x]), [])
                .forEach((diagramId: string) => {
                    apollonService
                        .find(Number(diagramId))
                        .map(({ body }) => body)
                        .toPromise()
                        .then((diagram: ApollonDiagram) => {
                            const componentRef = componentFactoryResolver.resolveComponentFactory(ModelingEditorComponent).create(injector);
                            componentRef.instance.readOnly = true;
                            componentRef.instance.umlModel = JSON.parse(diagram.jsonRepresentation);
                            appRef.attachView(componentRef.hostView);
                            const domElem = (componentRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
                            const apollonContainer = document.getElementById(`apollon-${diagramId}`);
                            if (apollonContainer) {
                                apollonContainer.innerHTML = '';
                                apollonContainer.append(domElem);
                            }
                        });
                });
            return replacedText;*/
        },
    };
    return extension;
};
