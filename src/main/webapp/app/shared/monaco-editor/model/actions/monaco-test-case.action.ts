import { TranslateService } from '@ngx-translate/core';
import { DomainActionWithOptionsArguments, MonacoEditorDomainActionWithOptions } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action-with-options.model';
import { CompletionItemKind, Disposable, MonacoEditorWithActions } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { ValueItem } from 'app/shared/markdown-editor/value-item.model';

/**
 * Action to insert a test case into the editor. It also registers a completion item provider offers all possible test cases as completion items to the user.
 */
export class MonacoTestCaseAction extends MonacoEditorDomainActionWithOptions {
    disposableCompletionProvider?: Disposable;

    static readonly ID = 'monaco-test-case.action';
    static readonly DEFAULT_INSERT_TEXT = 'testCaseName()';

    constructor() {
        super(MonacoTestCaseAction.ID, 'artemisApp.programmingExercise.problemStatement.testCaseCommand', undefined, undefined);
    }

    /**
     * Registers the action with the given editor and sets up the completion provider that offers all possible test cases to the user as they type.
     * @param editor The editor to register the action in.
     * @param translateService The translation service to use for translating the action label.
     * @throws error If the action is already registered with an editor or no model is attached to the editor.
     */
    register(editor: MonacoEditorWithActions, translateService: TranslateService) {
        super.register(editor, translateService);
        this.disposableCompletionProvider = this.registerCompletionProviderForCurrentModel<ValueItem>(
            editor,
            () => Promise.resolve(this.values),
            (item, range) => {
                return {
                    label: item.value,
                    kind: CompletionItemKind.Constant,
                    insertText: item.value,
                    range,
                    detail: 'Test',
                };
            },
        );
    }

    dispose() {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
    }

    /**
     * Executes the action in the current editor with the given arguments (selected item).
     * @param args
     */
    executeInCurrentEditor(args?: DomainActionWithOptionsArguments): void {
        super.executeInCurrentEditor(args);
    }

    run(editor: MonacoEditorWithActions, args?: DomainActionWithOptionsArguments) {
        this.replaceTextAtCurrentSelection(editor, args?.selectedItem?.value ?? MonacoTestCaseAction.DEFAULT_INSERT_TEXT);
        editor.focus();
    }

    getOpeningIdentifier(): string {
        return '(';
    }
}
