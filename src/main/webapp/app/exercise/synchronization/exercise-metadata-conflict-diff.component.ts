import { ChangeDetectionStrategy, Component, ViewEncapsulation, effect, input, output, viewChild } from '@angular/core';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';

@Component({
    selector: 'jhi-exercise-metadata-conflict-diff',
    template: `
        <jhi-monaco-diff-editor [allowSplitView]="allowSplitView()" [readOnly]="readOnly()" (modifiedContentChange)="modifiedChange.emit($event)"></jhi-monaco-diff-editor>
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    imports: [MonacoDiffEditorComponent],
})
/**
 * Lightweight wrapper around the Monaco diff editor for conflict resolution.
 */
export class ExerciseMetadataConflictDiffComponent {
    readonly monacoDiffEditor = viewChild.required(MonacoDiffEditorComponent);
    readonly original = input<string>('');
    readonly modified = input<string>('');
    readonly fileNameBase = input<string>('conflict');
    readonly allowSplitView = input<boolean>(true);
    readonly readOnly = input<boolean>(false);
    readonly modifiedChange = output<string>();

    constructor() {
        effect(() => {
            this.monacoDiffEditor().setFileContents(this.original(), this.modified(), `${this.fileNameBase()}-current`, `${this.fileNameBase()}-incoming`);
        });
    }

    /**
     * Returns the current modified text from the diff editor.
     */
    getModifiedText(): string {
        return this.monacoDiffEditor().getText().modified;
    }
}
