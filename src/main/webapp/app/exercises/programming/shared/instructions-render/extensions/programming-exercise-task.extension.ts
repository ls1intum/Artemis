import { EmbeddedViewRef, Injectable, Injector, ViewContainerRef } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingExerciseInstructionService } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-instruction-task-status.component';
import { TaskArray, TaskArrayWithExercise } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';
import { ArtemisShowdownExtensionWrapper } from 'app/shared/markdown-editor/extensions/artemis-showdown-extension-wrapper';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { Observable, Subject } from 'rxjs';
import { ShowdownExtension } from 'showdown';

/**
 * Regular expression for finding tasks.
 * A Task starts with the identifier `[task]` and the task name in square brackets.
 * This gets followed by a list of test cases in parentheses.
 * @example [task][Implement BubbleSort](testBubbleSort)
 *
 * The regular expression is used to find all tasks inside a problem statement and therefore uses the global flag.
 *
 * This is coupled to the value used in `ProgrammingExerciseTaskService` in the server.
 * If you change the regex, make sure to change it in all places!
 */
export const taskRegex = /\[task]\[([^[\]]+)]\(((?:[^(),]+(?:\([^()]*\)[^(),]*)?(?:,[^(),]+(?:\([^()]*\)[^(),]*)?)*)?)\)/g;

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseTaskExtensionWrapper implements ArtemisShowdownExtensionWrapper {
    // We don't have a provider for ViewContainerRef, so we pass it from ProgrammingExerciseInstructionComponent
    viewContainerRef: ViewContainerRef;

    private latestResult?: Result;
    private exercise: ProgrammingExercise;
    private testCases?: ProgrammingExerciseTestCase[];

    private testsForTaskSubject = new Subject<TaskArrayWithExercise>();
    private injectableElementsFoundSubject = new Subject<() => void>();

    // unique index, even if multiple tasks are shown from different problem statements on the same page (in different tabs)
    private taskIndex = 0;

    constructor(
        private programmingExerciseInstructionService: ProgrammingExerciseInstructionService,
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

    public setTestCases(testCases?: ProgrammingExerciseTestCase[]) {
        this.testCases = testCases;
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
        tasks.forEach(({ id, taskName, testIds }) => {
            const taskHtmlContainers = document.getElementsByClassName(`pe-task-${id}`);

            // The same task could appear multiple times in the instructions (edge case).
            for (let i = 0; i < taskHtmlContainers.length; i++) {
                // TODO: Replace this workaround with official Angular API replacement for ComponentFactoryResolver once available
                // See https://github.com/angular/angular/issues/45263#issuecomment-1082530357
                const componentRef = this.viewContainerRef.createComponent(ProgrammingExerciseInstructionTaskStatusComponent, { injector: this.injector });
                componentRef.instance.exercise = this.exercise;
                componentRef.instance.taskName = taskName;
                componentRef.instance.latestResult = this.latestResult;
                componentRef.instance.testIds = testIds;
                // wait for init and render to complete
                componentRef.changeDetectorRef.detectChanges();
                const domElem = (componentRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
                const taskHtmlContainer = taskHtmlContainers[i];
                taskHtmlContainer.replaceChildren(domElem);
            }
        });
    };

    /**
     * Creates and returns an extension to current exercise.
     * The task regex is coupled to the value used in ProgrammingExerciseTaskService in the server and
     * `TaskCommand` in the client
     * If you change the regex, make sure to change it in all places!
     */
    getExtension() {
        const extension: ShowdownExtension = {
            type: 'lang',
            filter: (problemStatement: string) => {
                const tasks = Array.from(problemStatement.matchAll(taskRegex));
                if (tasks) {
                    return this.createTasks(problemStatement, tasks);
                }
                return problemStatement;
            },
        };
        return extension;
    }

    private createTasks(problemStatement: string, tasks: RegExpMatchArray[]): string {
        const testsForTask: TaskArray = tasks
            // check that all groups (full match, name, tests) are present
            .filter((testMatch) => testMatch?.length === 3)
            .map((testMatch: RegExpMatchArray | null) => {
                const nextIndex = this.taskIndex;
                this.taskIndex++;
                return {
                    id: nextIndex,
                    completeString: testMatch![0],
                    taskName: testMatch![1],
                    testIds: testMatch![2] ? this.programmingExerciseInstructionService.convertTestListToIds(testMatch![2], this.testCases) : [],
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
                // Without class="d-flex" the injected components height would be 0.
                // Added zero-width space as content so the div actually consumes a line to prevent a <ol> display bug in Safari
                acc.replace(new RegExp(escapeStringForUseInRegex(task), 'g'), `<div class="pe-task-${id.toString()} d-flex">&#8203;</div>`),
            problemStatement,
        );
    }
}
