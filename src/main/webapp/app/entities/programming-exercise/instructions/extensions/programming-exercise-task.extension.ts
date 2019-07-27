import { ApplicationRef, ComponentFactoryResolver, EmbeddedViewRef, Injectable, Injector } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import * as showdown from 'showdown';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction-task-status.component';
import { Result } from 'app/entities/result';
import { escapeStringForUseInRegex } from 'app/utils/global.utils';
import { ProgrammingExerciseInstructionService } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction.service';
import { ArtemisShowdownExtensionWrapper } from 'app/markdown-editor/extensions/artemis-showdown-extension-wrapper';

export type Task = { completeString: string; taskName: string; tests: string[] };
export type TaskArray = Array<Task>;

@Injectable()
export class ProgrammingExerciseTaskExtensionWrapper implements ArtemisShowdownExtensionWrapper {
    private latestResult: Result | null = null;

    private testsForTaskSubject = new Subject<TaskArray>();
    private injectableElementsFoundSubject = new Subject<() => void>();

    constructor(
        private programmingExerciseInstructionService: ProgrammingExerciseInstructionService,
        private componentFactoryResolver: ComponentFactoryResolver,
        private appRef: ApplicationRef,
        private injector: Injector,
    ) {}

    public setLatestResult(result: Result | null) {
        this.latestResult = result;
    }

    public subscribeForFoundTestsInTasks() {
        return this.testsForTaskSubject.asObservable();
    }

    public subscribeForInjectableElementsFound(): Observable<() => void> {
        return this.injectableElementsFoundSubject.asObservable();
    }

    /**
     * For each task provided, inject a ProgrammingExerciseInstructionTaskStatusComponent into the container div.
     * @param tasks to inject into the html.
     */
    private injectTasks = (tasks: TaskArray) => {
        tasks.forEach(({ taskName, tests }, index: number) => {
            const componentRef = this.componentFactoryResolver.resolveComponentFactory(ProgrammingExerciseInstructionTaskStatusComponent).create(this.injector);
            componentRef.instance.taskName = taskName;
            componentRef.instance.latestResult = this.latestResult;
            componentRef.instance.tests = tests;

            this.appRef.attachView(componentRef.hostView);
            const domElem = (componentRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
            const taskHtmlContainer = document.getElementById(`task-${index}`);
            if (taskHtmlContainer) {
                taskHtmlContainer.innerHTML = '';
                taskHtmlContainer.append(domElem);
            }
        });
    };

    getExtension() {
        const extension: showdown.ShowdownExtension = {
            type: 'lang',
            filter: (text: string, converter: showdown.Converter, options: showdown.ConverterOptions) => {
                const idPlaceholder = '%idPlaceholder%';
                // E.g. [task][Implement BubbleSort](testBubbleSort)
                const taskRegex = /\[task\]\[.*\]\(.*\)/g;
                // E.g. Implement BubbleSort, testBubbleSort
                const innerTaskRegex = /\[task\]\[(.*)\]\((.*)\)/;
                // Without class="d-flex" the injected components height would be 0.
                const taskContainer = `<div id="task-${idPlaceholder}" class="d-flex"></div>`;
                const tasks = text.match(taskRegex) || [];
                const testsForTask: TaskArray = tasks
                    .map(task => {
                        return task.match(innerTaskRegex);
                    })
                    .filter(testMatch => !!testMatch && testMatch.length === 3)
                    .map((testMatch: RegExpMatchArray) => {
                        return { completeString: testMatch[0], taskName: testMatch[1], tests: testMatch[2].split(',').map(s => s.trim()) };
                    });
                this.testsForTaskSubject.next(testsForTask);
                // Emit new found elements that need to be injected into html after it is rendered.
                this.injectableElementsFoundSubject.next(() => this.injectTasks(testsForTask));
                return testsForTask.reduce(
                    (acc: string, { completeString: task, taskName, tests }, index: number): string =>
                        // Insert anchor divs into the text so that injectable elements can be inserted into them.
                        acc.replace(new RegExp(escapeStringForUseInRegex(task), 'g'), taskContainer.replace(idPlaceholder, index.toString())),
                    text,
                );
            },
        };
        return extension;
    }
}
