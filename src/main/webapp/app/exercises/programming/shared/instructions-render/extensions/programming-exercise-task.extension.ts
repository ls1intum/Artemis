import { ApplicationRef, ComponentFactoryResolver, EmbeddedViewRef, Injectable, Injector } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { ShowdownExtension } from 'showdown';
// tslint:disable-next-line:max-line-length
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-instruction-task-status.component';
import { Result } from 'app/entities/result.model';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { ProgrammingExerciseInstructionService } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { ArtemisShowdownExtensionWrapper } from 'app/shared/markdown-editor/extensions/artemis-showdown-extension-wrapper';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { TaskArray, TaskArrayWithExercise } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseTaskExtensionWrapper implements ArtemisShowdownExtensionWrapper {
    public exerciseHints: ExerciseHint[] = [];
    private latestResult?: Result;
    private exercise: Exercise;

    private testsForTaskSubject = new Subject<TaskArrayWithExercise>();
    private injectableElementsFoundSubject = new Subject<() => void>();

    // unique index, even if multiple tasks are shown from different problem statements on the same page (in different tabs)
    private taskIndex = 0;

    constructor(
        private programmingExerciseInstructionService: ProgrammingExerciseInstructionService,
        private componentFactoryResolver: ComponentFactoryResolver,
        private appRef: ApplicationRef,
        private injector: Injector,
    ) {}

    /**
     * Sets latest result according to parameter.
     * @param result - either a result or undefined.
     */
    public setLatestResult(result: Result | undefined) {
        this.latestResult = result;
    }

    /**
     * Sets the exercise. This is needed as multiple instructions components use this service in parallel and we have to
     * associate tasks with an exercise to identify tasks properly
     * @param exercise - the current exercise.
     */
    public setExercise(exercise: Exercise) {
        this.exercise = exercise;
    }

    /**
     * Subscribes to testsForTaskSubject.
     */
    public subscribeForFoundTestsInTasks() {
        return this.testsForTaskSubject.asObservable();
    }

    /**
     * Subscribes to injectableElementsFoundSubject.
     */
    public subscribeForInjectableElementsFound(): Observable<() => void> {
        return this.injectableElementsFoundSubject.asObservable();
    }

    /**
     * For each task provided, inject a ProgrammingExerciseInstructionTaskStatusComponent into the container div.
     * @param tasks to inject into the html.
     */
    private injectTasks = (tasks: TaskArray) => {
        tasks.forEach(({ id, taskName, tests, hints }) => {
            const taskHtmlContainers = document.getElementsByClassName(`pe-task-${id}`);

            // The same task could appear multiple times in the instructions (edge case).
            for (let i = 0; i < taskHtmlContainers.length; i++) {
                const componentRef = this.componentFactoryResolver.resolveComponentFactory(ProgrammingExerciseInstructionTaskStatusComponent).create(this.injector);
                componentRef.instance.exerciseHints = this.exerciseHints.filter((hint) => hints.includes(hint.id!.toString(10)));
                componentRef.instance.taskName = taskName;
                componentRef.instance.latestResult = this.latestResult;
                componentRef.instance.tests = tests;
                componentRef.instance.showTestDetails =
                    (this.exercise.type === ExerciseType.PROGRAMMING && (this.exercise as ProgrammingExercise).showTestNamesToStudents) || false;

                this.appRef.attachView(componentRef.hostView);
                const domElem = (componentRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
                const taskHtmlContainer = taskHtmlContainers[i];
                taskHtmlContainer.innerHTML = '';
                taskHtmlContainer.append(domElem);
            }
        });
    };

    /**
     * Creates and returns an extension to current exercise.
     */
    getExtension() {
        const extension: ShowdownExtension = {
            type: 'lang',
            filter: (text: string) => {
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
                        const nextIndex = this.taskIndex;
                        this.taskIndex++;
                        return {
                            id: nextIndex,
                            completeString: testMatch[0],
                            taskName: testMatch[1],
                            // split the names by "," only when there is not a closing bracket without a previous opening bracket
                            tests: testMatch[2].split(/,(?![^(]*?\))/).map((s) => s.trim()),
                            hints: testMatch[4] ? testMatch[4].split(',').map((s) => s.trim()) : [],
                        };
                    });
                const tasksWithParticipationId: TaskArrayWithExercise = {
                    exerciseId: this.exercise.id!,
                    tasks: testsForTask,
                };
                this.testsForTaskSubject.next(tasksWithParticipationId);
                // Emit new found elements that need to be injected into html after it is rendered.
                this.injectableElementsFoundSubject.next(() => {
                    this.injectTasks(testsForTask);
                });
                return testsForTask.reduce(
                    (acc: string, { completeString: task, id }): string =>
                        // Insert anchor divs into the text so that injectable elements can be inserted into them.
                        acc.replace(new RegExp(escapeStringForUseInRegex(task), 'g'), taskContainer.replace(idPlaceholder, id.toString())),
                    text,
                );
            },
        };
        return extension;
    }
}
