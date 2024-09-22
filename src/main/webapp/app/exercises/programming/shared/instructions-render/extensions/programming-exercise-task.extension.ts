import { Injectable, ViewContainerRef } from '@angular/core';
import { TaskArrayWithExercise } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';
import { ArtemisTextReplacementExtension } from 'app/shared/markdown-editor/extensions/ArtemisTextReplacementExtension';
import { Observable, Subject } from 'rxjs';

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
export class ProgrammingExerciseTaskExtensionWrapper extends ArtemisTextReplacementExtension {
    // We don't have a provider for ViewContainerRef, so we pass it from ProgrammingExerciseInstructionComponent
    viewContainerRef: ViewContainerRef;

    private testsForTaskSubject = new Subject<TaskArrayWithExercise>();
    private injectableElementsFoundSubject = new Subject<() => void>();
    /**
     * Subscribes to injectableElementsFoundSubject.
     */
    public subscribeForInjectableElementsFound(): Observable<() => void> {
        return this.injectableElementsFoundSubject.asObservable();
    }

    /**
     * The task regex is coupled to the value used in ProgrammingExerciseTaskService in the server
     * and `TaskCommand` in the client
     * If you change the regex, make sure to change it in all places!
     */
    replaceText(text: string): string {
        return text.replace(taskRegex, (match) => {
            return this.escapeTaskSpecialCharactersForMarkdown(match);
        });
    }

    private escapeTaskSpecialCharactersForMarkdown = (text: string) => {
        // We want to avoid special characters (such as underscores) in the task or test case names to be interpreted as markdown, as this may interfere with the preview.
        return text.replace(/[`.*_+\-!${}()|[\]\\]/g, '\\$&');
    };
}
