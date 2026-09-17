import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { MetisService } from 'app/communication/service/metis.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { TextEditorDomainActionWithOptions } from 'app/editor/monaco-editor/model/actions/text-editor-domain-action-with-options.model';
import { ValueItem } from 'app/editor/markdown-editor/value-item.model';
import { Disposable } from 'app/editor/monaco-editor/model/actions/monaco-editor.util';
import { TextEditor } from 'app/editor/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorCompletionItem, TextEditorCompletionItemKind } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-completion-item.model';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';

/**
 * Action to insert a reference to an exercise into the editor. Users that type a / will see a list of available exercises to reference.
 */
export class ExerciseReferenceAction extends TextEditorDomainActionWithOptions {
    static readonly ID = 'exercise-reference.action';
    static readonly DEFAULT_INSERT_TEXT = '/exercise';

    disposableCompletionProvider?: Disposable;

    /** The in-flight or completed title load, shared by every completion invocation and cleared when it fails. */
    private titleLoad?: Promise<ValueItem[]>;

    constructor(
        private readonly metisService: MetisService,
        private readonly exerciseService: ExerciseService,
    ) {
        super(ExerciseReferenceAction.ID, 'artemisApp.metis.editor.exercise');
    }

    /**
     * The exercises that can be referenced, fetched once and shared by every completion invocation.
     *
     * Fetched rather than read off the course: the course overview loads each tab's content on demand, so the course
     * only carries its exercises while the exercises tab happens to be open.
     *
     * The completion provider awaits this rather than reading whatever has arrived so far, so typing `/exercise` before
     * the response lands still lists the exercises instead of nothing. A failed load is cleared rather than cached, so
     * the next invocation retries instead of leaving the editor permanently empty.
     */
    private loadTitles(): Promise<ValueItem[]> {
        this.titleLoad ??= firstValueFrom(this.exerciseService.getTitlesForCourse(this.metisService.getCourse().id!))
            .then((exercises) => {
                const values = exercises
                    .filter((exercise) => !!exercise.title)
                    .map((exercise) => ({
                        id: exercise.id.toString(),
                        value: exercise.title!,
                        type: exercise.type,
                    }));
                this.setValues(values);
                return values;
            })
            .catch(() => {
                this.titleLoad = undefined;
                return [];
            });
        return this.titleLoad;
    }

    /**
     * Registers this action in the provided editor. This will register a completion provider that shows the available exercises.
     * @param editor The editor to register the completion provider for.
     * @param translateService The translate service to use for translations.
     */
    override register(editor: TextEditor, translateService: TranslateService): void {
        super.register(editor, translateService);
        this.disposableCompletionProvider = this.registerCompletionProviderForCurrentModel<ValueItem>(
            editor,
            () => this.loadTitles(),
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

    override dispose(): void {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
    }

    getOpeningIdentifier(): string {
        return '[exercise]';
    }
}
