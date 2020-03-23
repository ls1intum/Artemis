import { ApplicationRef, ComponentFactoryResolver, EmbeddedViewRef, Injectable, Injector } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import * as showdown from 'showdown';
// tslint:disable-next-line:max-line-length
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-instruction-task-status.component';
import { Result } from 'app/entities/result.model';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { ProgrammingExerciseInstructionService } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { ArtemisShowdownExtensionWrapper } from 'app/shared/markdown-editor/extensions/artemis-showdown-extension-wrapper';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { TaskArray } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseTaskExtensionWrapper implements ArtemisShowdownExtensionWrapper {
    public exerciseHints: ExerciseHint[] = [];
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
        tasks.forEach(({ taskName, tests, hints }, index: number) => {
            const taskHtmlContainers = document.getElementsByClassName(`pe-task-${index}`);

            // The same task could appear multiple times in the instructions (edge case).
            for (let i = 0; i < taskHtmlContainers.length; i++) {
                const componentRef = this.componentFactoryResolver.resolveComponentFactory(ProgrammingExerciseInstructionTaskStatusComponent).create(this.injector);
                componentRef.instance.exerciseHints = this.exerciseHints.filter(({ id }) => hints.includes(id.toString(10)));
                componentRef.instance.taskName = taskName;
                componentRef.instance.latestResult = this.latestResult;
                componentRef.instance.tests = tests;

                this.appRef.attachView(componentRef.hostView);
                const domElem = (componentRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
                const taskHtmlContainer = taskHtmlContainers[i];
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
                const taskRegex = /\[task\]\[.*\]\(.*\)({.*})?/g;
                // E.g. Implement BubbleSort, testBubbleSort
                const innerTaskRegex = /\[task\]\[(.*)\]\((.*)\)({(.*)})?/;
                // Without class="d-flex" the injected components height would be 0.
                const taskContainer = `<div class="pe-task-${idPlaceholder} d-flex"></div>`;
                const tasks = text.match(taskRegex) || [];
                const testsForTask: TaskArray = tasks
                    .map((task) => {
                        return task.match(innerTaskRegex);
                    })
                    // Legacy tasks don't contain the hint list, so there are 2 cases (with or without hints).
                    .filter((testMatch) => !!testMatch && (testMatch.length === 3 || testMatch.length === 5))
                    .map((testMatch: RegExpMatchArray) => {
                        return {
                            completeString: testMatch[0],
                            taskName: testMatch[1],
                            tests: testMatch[2].split(',').map((s) => s.trim()),
                            hints: testMatch[4] ? testMatch[4].split(',').map((s) => s.trim()) : [],
                        };
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
