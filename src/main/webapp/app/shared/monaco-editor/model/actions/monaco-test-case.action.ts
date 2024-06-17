import { MonacoEditorInsertAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-insert-action.model';
import * as monaco from 'monaco-editor';

const INSERT_TEST_CASE_TEXT = 'testCaseName()';
interface TestCaseValue {
    value: string;
    id: string;
}
export class MonacoTestCaseAction extends MonacoEditorInsertAction {
    possibleValues: TestCaseValue[] = [];
    disposableCompletionProvider?: monaco.IDisposable;

    static readonly ID = 'monaco-test-case.action';

    constructor(label: string, translationKey: string) {
        super(MonacoTestCaseAction.ID, label, translationKey, undefined, undefined, INSERT_TEST_CASE_TEXT);
    }

    /**
     * Registers the action with the given editor and sets up the completion provider.
     * @param editor The editor to register the action in.
     * @throws error If the action is already registered with an editor or no model is attached to the editor.
     */
    register(editor: monaco.editor.IStandaloneCodeEditor) {
        super.register(editor);
        const model = editor.getModel();
        if (model) {
            const languageId = model.getLanguageId();
            const modelId = model.id;
            this.disposableCompletionProvider = monaco.languages.registerCompletionItemProvider(languageId, {
                provideCompletionItems: (model: monaco.editor.ITextModel, position: monaco.Position) => {
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
                        suggestions: this.possibleValues.map((value) => ({
                            label: value.value,
                            kind: monaco.languages.CompletionItemKind.Constant,
                            insertText: value.value,
                            range,
                            detail: 'Test',
                        })),
                    };
                },
            });
        } else {
            throw new Error(`A model must be attached to the editor to use the ${this.id} action.`);
        }
    }

    dispose() {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
    }

    run(editor: monaco.editor.ICodeEditor, args?: string) {
        if (!args) {
            super.run(editor);
        } else {
            this.replaceTextAtCurrentSelection(editor, args);
            editor.focus();
        }
    }
}
