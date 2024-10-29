import { TranslateService } from '@ngx-translate/core';
import { DomainActionWithOptionsArguments, TextEditorDomainActionWithOptions } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action-with-options.model';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { ValueItem } from 'app/shared/markdown-editor/value-item.model';
import { TextEditor } from './adapter/text-editor.interface';
import { TextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorCompletionItem, TextEditorCompletionItemKind } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-completion-item.model';

/**
 * Action to insert a test case into the editor. It also registers a completion item provider offers all possible test cases as completion items to the user.
 */
export class TestCaseAction extends TextEditorDomainActionWithOptions {
    disposableCompletionProvider?: Disposable;

    static readonly ID = 'test-case.action';
    static readonly DEFAULT_INSERT_TEXT = 'testCaseName()';

    constructor() {
        super(TestCaseAction.ID, 'artemisApp.programmingExercise.problemStatement.testCaseCommand', undefined, undefined);
    }

    /**
     * Registers the action with the given editor and sets up the completion provider that offers all possible test cases to the user as they type.
     * @param editor The editor to register the action in.
     * @param translateService The translation service to use for translating the action label.
     * @throws error If the action is already registered with an editor or no model is attached to the editor.
     */
    register(editor: TextEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        this.disposableCompletionProvider = this.registerCompletionProviderForCurrentModel<ValueItem>(
            editor,
            () => Promise.resolve(this.values),
            (item: ValueItem, range: TextEditorRange) => new TextEditorCompletionItem(item.value, 'Test', item.value, TextEditorCompletionItemKind.Default, range),
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

    run(editor: TextEditor, args?: DomainActionWithOptionsArguments) {
        this.replaceTextAtCurrentSelection(editor, args?.selectedItem?.value ?? TestCaseAction.DEFAULT_INSERT_TEXT);
        editor.focus();
    }

    getOpeningIdentifier(): string {
        return '(';
    }
}
