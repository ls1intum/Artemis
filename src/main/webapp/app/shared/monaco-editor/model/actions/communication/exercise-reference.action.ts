import { TranslateService } from '@ngx-translate/core';
import { MetisService } from 'app/shared/metis/metis.service';
import { TextEditorDomainActionWithOptions } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action-with-options.model';
import { ValueItem } from 'app/shared/markdown-editor/value-item.model';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorCompletionItem, TextEditorCompletionItemKind } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-completion-item.model';
import { TextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';

/**
 * Action to insert a reference to an exercise into the editor. Users that type a / will see a list of available exercises to reference.
 */
export class ExerciseReferenceAction extends TextEditorDomainActionWithOptions {
    static readonly ID = 'exercise-reference.action';
    static readonly DEFAULT_INSERT_TEXT = '/exercise';

    disposableCompletionProvider?: Disposable;

    constructor(private readonly metisService: MetisService) {
        super(ExerciseReferenceAction.ID, 'artemisApp.metis.editor.exercise');
    }

    /**
     * Registers this action in the provided editor. This will register a completion provider that shows the available exercises.
     * @param editor The editor to register the completion provider for.
     * @param translateService The translate service to use for translations.
     */
    register(editor: TextEditor, translateService: TranslateService): void {
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
            (item: ValueItem, range: TextEditorRange) =>
                new TextEditorCompletionItem(
                    `/exercise ${item.value}`,
                    item.type,
                    `[${item.type}]${item.value}(${this.metisService.getLinkForExercise(item.id)})[/${item.type}]`,
                    TextEditorCompletionItemKind.Default,
                    range,
                ),
            '/',
        );
    }

    /**
     * Inserts the text '/exercise' into the editor and focuses it. This method will trigger the completion provider to show the available exercises.
     * @param editor The editor to insert the text into.
     */
    run(editor: TextEditor): void {
        this.replaceTextAtCurrentSelection(editor, ExerciseReferenceAction.DEFAULT_INSERT_TEXT);
        editor.triggerCompletion();
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
