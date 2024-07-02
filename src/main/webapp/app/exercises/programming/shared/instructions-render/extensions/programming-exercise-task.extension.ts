import { Injectable, ViewContainerRef } from '@angular/core';
import { TaskArrayWithExercise } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';
import { ArtemisShowdownExtensionWrapper } from 'app/shared/markdown-editor/extensions/artemis-showdown-extension-wrapper';
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

    private testsForTaskSubject = new Subject<TaskArrayWithExercise>();
    private injectableElementsFoundSubject = new Subject<() => void>();

    constructor() {}

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
