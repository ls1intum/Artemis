import { TranslateService } from '@ngx-translate/core';
import * as monaco from 'monaco-editor';
import { MetisService } from 'app/shared/metis/metis.service';
import { MonacoEditorDomainActionWithOptions } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action-with-options.model';
import { ValueItem } from 'app/shared/markdown-editor/command-constants';

export class MonacoExerciseReferenceAction extends MonacoEditorDomainActionWithOptions {
    static readonly ID = 'monaco-exercise-reference.action';
    static readonly DEFAULT_INSERT_TEXT = '/exercise';

    disposableCompletionProvider?: monaco.IDisposable;

    constructor(private readonly metisService: MetisService) {
        super(MonacoExerciseReferenceAction.ID, 'artemisApp.metis.editor.exercise');
    }

    register(editor: monaco.editor.IStandaloneCodeEditor, translateService: TranslateService) {
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
                detail: item.type ?? this.label,
            }),
            '/',
        );
    }

    run(editor: monaco.editor.ICodeEditor): void {
        this.typeText(editor, MonacoExerciseReferenceAction.DEFAULT_INSERT_TEXT);
        editor.focus();
    }

    dispose() {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
    }
}
