import { TranslateService } from '@ngx-translate/core';
import * as monaco from 'monaco-editor';
import { MetisService } from 'app/shared/metis/metis.service';
import { DomainActionWithOptionsArguments, MonacoEditorDomainActionWithOptions } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action-with-options.model';

export class MonacoExerciseReferenceAction extends MonacoEditorDomainActionWithOptions {
    static readonly ID = 'monaco-exercise-reference.action';
    static readonly DEFAULT_INSERT_TEXT = '/exercise';

    disposableCompletionProvider?: monaco.IDisposable;

    constructor(private readonly metisService: MetisService) {
        super(MonacoExerciseReferenceAction.ID, 'artemisApp.metis.editor.exercise');
    }

    register(editor: monaco.editor.IStandaloneCodeEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        const model = editor.getModel();
        const exercises = this.metisService.getCourse().exercises ?? [];
        this.setValues(
            exercises.map((exercise) => ({
                id: exercise.id!.toString(),
                value: exercise.title!,
                type: exercise.type,
            })),
        );
        if (!model) {
            throw new Error(`A model must be attached to the editor to use the ${this.id} action.`);
        }
        const languageId = model.getLanguageId();
        const modelId = model.id;
        this.disposableCompletionProvider = monaco.languages.registerCompletionItemProvider(languageId, {
            triggerCharacters: ['/'],
            provideCompletionItems: (model: monaco.editor.ITextModel, position: monaco.Position): monaco.languages.ProviderResult<monaco.languages.CompletionList> => {
                if (model.id !== modelId) {
                    return undefined;
                }
                const wordUntilPosition = model.getWordUntilPosition(position);
                const range = {
                    startLineNumber: position.lineNumber,
                    startColumn: wordUntilPosition.startColumn - 1,
                    endLineNumber: position.lineNumber,
                    endColumn: wordUntilPosition.endColumn,
                };
                // Check if before the word, we have a #
                const beforeWord = model.getValueInRange({
                    startLineNumber: position.lineNumber,
                    startColumn: wordUntilPosition.startColumn - 1,
                    endLineNumber: position.lineNumber,
                    endColumn: wordUntilPosition.startColumn,
                });
                if (wordUntilPosition.word !== '/') {
                    if (beforeWord !== '/') {
                        return undefined;
                    }
                }

                return {
                    suggestions: this.getValues().map((item) => ({
                        label: `/exercise ${item.value}`,
                        kind: monaco.languages.CompletionItemKind.Constant,
                        insertText: `[${item.type}]${item.value}(${this.metisService.getLinkForExercise(item.id)})[/${item.type}]`,
                        range,
                        detail: item.type ?? 'Exercise',
                    })),
                };
            },
        });
    }

    run(editor: monaco.editor.ICodeEditor, args?: DomainActionWithOptionsArguments): void {
        if (!args?.selectedItem) {
            editor.trigger('keyboard', 'type', { text: MonacoExerciseReferenceAction.DEFAULT_INSERT_TEXT });
            editor.focus();
        } else {
            const item = args.selectedItem;
            this.replaceTextAtCurrentSelection(editor, `[${item.type}]${item.value}(${this.metisService.getLinkForExercise(item.id)})[/${item.type}]`);
        }
    }

    dispose() {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
    }
}
