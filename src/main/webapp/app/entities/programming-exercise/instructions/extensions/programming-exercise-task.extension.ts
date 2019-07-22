import { ApplicationRef, ComponentFactoryResolver, EmbeddedViewRef, Injector, Injectable } from '@angular/core';
import * as showdown from 'showdown';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/entities/programming-exercise';
import { Result } from 'app/entities/result';
import { escapeStringForUseInRegex } from 'app/utils/global.utils';

@Injectable()
export class ProgrammingExerciseTaskExtensionFactory {
    private latestResult: Result | null = null;

    constructor(private componentFactoryResolver: ComponentFactoryResolver, private appRef: ApplicationRef, private injector: Injector) {}

    public setLatestResult(result: Result | null) {
        this.latestResult = result;
    }

    getExtension() {
        const extension: showdown.ShowdownExtension = {
            type: 'lang',
            filter: (text: string, converter: showdown.Converter, options: showdown.ConverterOptions) => {
                const idPlaceholder = '%idPlaceholder%';
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
                const replacedText = testsForTask.reduce(
                    (acc: string, [task, taskName, tests]: [string, string, string[]], index: number): string =>
                        acc.replace(new RegExp(escapeStringForUseInRegex(task), 'g'), taskContainer.replace(idPlaceholder, index.toString())),
                    text,
                );
                setTimeout(() => {
                    testsForTask.forEach(([, taskName, tests]: [string, string, string[]], index: number) => {
                        /*                        const [done] = this.statusForTests(tests);*/
                        const componentRef = this.componentFactoryResolver.resolveComponentFactory(ProgrammingExerciseInstructionTaskStatusComponent).create(this.injector);
                        componentRef.instance.taskName = taskName;
                        componentRef.instance.latestResult = this.latestResult;
                        componentRef.instance.tests = tests;

                        this.appRef.attachView(componentRef.hostView);
                        const domElem = (componentRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
                        const taskContainer = document.getElementById(`task-${index}`)!;
                        taskContainer.innerHTML = '';
                        taskContainer.append(domElem);
                    });
                }, 0);
                return replacedText;
            },
        };
        return extension;
    }
}
