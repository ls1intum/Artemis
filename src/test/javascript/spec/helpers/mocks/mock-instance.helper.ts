import { MockInstance } from 'ng-mocks';
import { CodeEditorMonacoComponent } from 'app/exercises/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { signal } from '@angular/core';

/*
 * This file contains mock instances for the tests where they would otherwise fail due to the use of a signal-based viewChild or contentChild with MockComponent(SomeComponent).
 * This is a workaround for https://github.com/help-me-mom/ng-mocks/issues/8634. Once the issue is resolved, this file and the functions it defines can be removed.
 */

/**
 * Overwrites the signal-based viewChild queries of the CodeEditorMonacoComponent so it can be mocked using MockComponent.
 * Workaround for https://github.com/help-me-mom/ng-mocks/issues/8634.
 */
export function mockCodeEditorMonacoViewChildren() {
    MockInstance.scope('case');
    MockInstance(CodeEditorMonacoComponent, () => ({
        editor: signal<any>({}),
        inlineFeedbackComponents: signal<any[]>([]),
        inlineFeedbackSuggestionComponents: signal<any[]>([]),
    }));
}
