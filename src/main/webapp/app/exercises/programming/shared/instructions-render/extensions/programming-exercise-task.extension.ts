import { ApplicationRef, EnvironmentInjector, Injectable, ViewContainerRef, createComponent } from '@angular/core';
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
const taskRegex = /\[task]\[([^[\]]+)]\(((?:[^(),]+(?:\([^()]*\)[^(),]*)?(?:,[^(),]+(?:\([^()]*\)[^(),]*)?)*)?)\)/g;

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseTaskExtensionWrapper implements ArtemisShowdownExtensionWrapper {
    // We don't have a provider for ViewContainerRef, so we pass it from ProgrammingExerciseInstructionComponent
    viewContainerRef: ViewContainerRef;

    private latestResult?: Result;
    private exercise: ProgrammingExercise;
    private testCases?: ProgrammingExerciseTestCase[];

    private testsForTaskSubject = new Subject<TaskArrayWithExercise>();
    private injectableElementsFoundSubject = new Subject<() => void>();
    private testsForTask: TaskArray;

    // unique index, even if multiple tasks are shown from different problem statements on the same page (in different tabs)
    private taskIndex = 0;

    constructor(
        private programmingExerciseInstructionService: ProgrammingExerciseInstructionService,
        private appRef: ApplicationRef,
        private injector: EnvironmentInjector,
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
     * Creates and returns an extension to current exercise.
     * The task regex is coupled to the value used in ProgrammingExerciseTaskService in the server and
     * `TaskCommand` in the client
     * If you change the regex, make sure to change it in all places!
     */
    getExtension() {
        const extension: ShowdownExtension = {
            type: 'lang',
            filter: (problemStatement: string) => {
                return this.createTasks(problemStatement);
            },
        };
        return extension;
    }

    public createTasks(problemStatement: string): string {
        return problemStatement.replace(taskRegex, (match) => {
            return this.escapeTaskSpecialCharactersForMarkdown(match);
        });
    }

    private escapeTaskSpecialCharactersForMarkdown = (text: string) => {
        return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    };
}
