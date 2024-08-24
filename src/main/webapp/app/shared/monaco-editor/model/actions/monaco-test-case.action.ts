import { TranslateService } from '@ngx-translate/core';
import * as monaco from 'monaco-editor';
import { DomainActionWithOptionsArguments, MonacoEditorDomainActionWithOptions } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action-with-options.model';

/**
 * Action to insert a test case into the editor. It also registers a completion item provider offers all possible test cases as completion items to the user.
 */
export class MonacoTestCaseAction extends MonacoEditorDomainActionWithOptions {
    disposableCompletionProvider?: monaco.IDisposable;

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
    register(editor: monaco.editor.IStandaloneCodeEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        const model = editor.getModel();
        if (!model) {
            throw new Error(`A model must be attached to the editor to use the ${this.id} action.`);
        }
        const languageId = model.getLanguageId();
        const modelId = model.id;
        this.disposableCompletionProvider = monaco.languages.registerCompletionItemProvider(languageId, {
            provideCompletionItems: (model: monaco.editor.ITextModel, position: monaco.Position): monaco.languages.CompletionList | undefined => {
                if (model.id !== modelId) {
                    return undefined;
                }
                // Replace the current word with the inserted test case
                const wordUntilPosition = model.getWordUntilPosition(position);
                const range = {
                    startLineNumber: position.lineNumber,
                    startColumn: wordUntilPosition.startColumn,
                    endLineNumber: position.lineNumber,
                    endColumn: wordUntilPosition.endColumn,
                };

                // We can simply map all possible values here. The Monaco editor filters the items based on the user input.
                return {
                    suggestions: this.values.map((value) => ({
                        label: value.value,
                        kind: monaco.languages.CompletionItemKind.Constant,
                        insertText: value.value,
                        range,
                        detail: 'Test',
                    })),
                };
            },
        });
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

    run(editor: monaco.editor.ICodeEditor, args?: DomainActionWithOptionsArguments) {
        this.replaceTextAtCurrentSelection(editor, args?.selectedItem?.value ?? MonacoTestCaseAction.DEFAULT_INSERT_TEXT);
        editor.focus();
    }

    getOpeningIdentifier(): string {
        return '(';
    }
}
