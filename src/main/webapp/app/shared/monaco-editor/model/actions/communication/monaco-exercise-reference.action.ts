import { TranslateService } from '@ngx-translate/core';
import * as monaco from 'monaco-editor';
import { MetisService } from 'app/shared/metis/metis.service';
import { MonacoEditorDomainActionWithOptions } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action-with-options.model';
import { ValueItem } from 'app/shared/markdown-editor/value-item.model';

/**
 * Action to insert a reference to an exercise into the editor. Users that type a / will see a list of available exercises to reference.
 */
export class MonacoExerciseReferenceAction extends MonacoEditorDomainActionWithOptions {
    static readonly ID = 'monaco-exercise-reference.action';
    static readonly DEFAULT_INSERT_TEXT = '/exercise';

    disposableCompletionProvider?: monaco.IDisposable;

    constructor(private readonly metisService: MetisService) {
        super(MonacoExerciseReferenceAction.ID, 'artemisApp.metis.editor.exercise');
    }

    /**
     * Registers this action in the provided editor. This will register a completion provider that shows the available exercises.
     * @param editor The editor to register the completion provider for.
     * @param translateService The translate service to use for translations.
     */
    register(editor: monaco.editor.IStandaloneCodeEditor, translateService: TranslateService): void {
        super.register(editor, translateService);
        const exercises = this.metisService.getCourse().exercises ?? [];
        this.setValues(
            exercises.map((exercise) => ({
                id: exercise.id!.toString(),
                value: exercise.title!,
                type: exercise.type,
            })),
        );
        this.disposableCompletionProvider = this.registerCompletionProviderForCurrentModel<ValueItem>(
            editor,
            () => Promise.resolve(this.getValues()),
            (item: ValueItem, range: monaco.IRange) => ({
                label: `/exercise ${item.value}`,
                kind: monaco.languages.CompletionItemKind.Constant,
                insertText: `[${item.type}]${item.value}(${this.metisService.getLinkForExercise(item.id)})[/${item.type}]`,
                range,
                detail: item.type!,
            }),
            '/',
        );
    }

    /**
     * Types the text '/exercise' into the editor and focuses it. This will trigger the completion provider to show the available exercises.
     * @param editor The editor to type the text into.
     */
    run(editor: monaco.editor.ICodeEditor): void {
        this.typeText(editor, MonacoExerciseReferenceAction.DEFAULT_INSERT_TEXT);
        editor.focus();
    }

    dispose(): void {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
    }

    getOpeningIdentifier(): string {
        return '[exercise]';
    }
}
